package in.virit;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import in.virit.ibbq.IBBQListener;
import in.virit.ibbq.IBBQThermometer;
import in.virit.ibbq.TemperatureUpdate;
import in.virit.meater.cloud.MeaterCloudClient;
import in.virit.meater.cloud.MeaterCloudListener;
import in.virit.meater.cloud.MeaterDevice;
import in.virit.mcp9600.Mcp9600;
import in.virit.pwmchip.PwmChip;
import in.virit.pwmchip.Servo;
import in.virit.pwmchip.Sg90Servo;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SmokerHardware {

    private static final Logger LOG = Logger.getLogger(SmokerHardware.class.getName());
    private static final int MAX_HISTORY = 720; // 1 hour at 5s interval

    static final Path LOG_DIR = Path.of(System.getProperty("user.home"), ".j-smoker");
    static final Path LOG_FILE = LOG_DIR.resolve("smoker.log");
    static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static final String PROBE = "Probe";
    public static final String CHIP = "Chip";
    public static final String IBBQ_1 = "iBBQ 1 (chamber)";
    public static final String IBBQ_2 = "iBBQ 2 (food)";
    public static final String IBBQ_3 = "iBBQ 3 (food)";
    public static final String MEATER_PREFIX = "Meater ";
    private static final String[] IBBQ_KEYS = {IBBQ_1, IBBQ_2, IBBQ_3};

    static int FAN_GPIO = 25;

    // Software PWM for blower
    private static final long BLOWER_CYCLE_MS = 10_000; // 10 second full cycle
    private static final long BLOWER_MIN_PULSE_MS = 300; // minimum on-time

    private Context pi4j;

    private Servo servo;
    private DigitalOutput fanOutput;
    private Mcp9600 mcp9600;
    private boolean hardwareAvailable;
    private boolean devMode;

    private IBBQThermometer ibbqThermometer;
    private boolean ibbqAvailable;
    private boolean ibbqConnectionAttempted;

    @ConfigProperty(name = "meater.email")
    Optional<String> meaterEmail;
    @ConfigProperty(name = "meater.password")
    Optional<String> meaterPassword;

    private MeaterCloudClient meaterClient;
    private boolean meaterAvailable;
    private boolean meaterConnectionAttempted;

    // Blower state
    private boolean blowerForceOn;
    private boolean blowerSoftPwmEnabled;
    private int blowerDutyPercent; // 1-100
    private long blowerCycleStart;

    // Actuator state tracking for auto-control
    private volatile int currentThrottlePercent;
    private volatile int currentBlowerPercent;
    private volatile boolean automaticControlActive;

    // Simulation mode: when enabled, simulateReading() injects values instead of real sensors
    private volatile boolean simulationMode;

    public record TemperatureReading(Instant timestamp, double temperature) {}

    private final Map<String, CopyOnWriteArrayList<TemperatureReading>> history = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        history.put(PROBE, new CopyOnWriteArrayList<>());
        history.put(CHIP, new CopyOnWriteArrayList<>());
        history.put(IBBQ_1, new CopyOnWriteArrayList<>());
        history.put(IBBQ_2, new CopyOnWriteArrayList<>());
        history.put(IBBQ_3, new CopyOnWriteArrayList<>());

        // Restore persisted history and prune old entries
        restoreFromLog();
        pruneLog();

        if (!new java.io.File("/dev/i2c-1").exists()) {
            LOG.info("Hardware not detected, running in UI-only mode with fake data");
            devMode = true;
            prefillDummyHistory();
            return;
        }

        // Meater Cloud works without local hardware but is pointless in dev mode
        Thread.ofVirtual().name("meater-connect").start(this::connectMeater);

        try {
            pi4j = Pi4J.newAutoContext();

            servo = new Sg90Servo(new PwmChip(0, 0));
            servo.init();

            fanOutput = pi4j.digitalOutput().create(FAN_GPIO);

            mcp9600 = Mcp9600.create(pi4j);

            hardwareAvailable = true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Hardware init failed, running in UI-only mode", e);
        }

        Thread.ofVirtual().name("ibbq-connect").start(this::connectIbbq);
    }

    @Scheduled(every = "5s")
    void readTemperatures() {
        if (simulationMode) return; // Simulation injects readings directly
        Instant now = Instant.now();
        var temps = new java.util.LinkedHashMap<String, Double>();
        if (hardwareAvailable) {
            double probeTemp = mcp9600.getHotJunctionTemperature();
            double chipTemp = mcp9600.getColdJunctionTemperature();
            addReading(PROBE, new TemperatureReading(now, probeTemp));
            addReading(CHIP, new TemperatureReading(now, chipTemp));
            temps.put(PROBE, probeTemp);
            temps.put(CHIP, chipTemp);
        } else if (devMode) {
            double probeTemp = 225.0 + Math.random() * 10 - 5;
            double chipTemp = 42.0 + Math.random() * 2 - 1;
            double ibbq1 = 180.0 + Math.random() * 10 - 5;
            double ibbq2 = 72.0 + Math.random() * 4 - 2;
            double ibbq3 = 68.0 + Math.random() * 4 - 2;
            double meaterTip = 74.0 + Math.random() * 4 - 2;
            double meaterAmbient = 195.0 + Math.random() * 10 - 5;
            addReading(PROBE, new TemperatureReading(now, probeTemp));
            addReading(CHIP, new TemperatureReading(now, chipTemp));
            addReading(IBBQ_1, new TemperatureReading(now, ibbq1));
            addReading(IBBQ_2, new TemperatureReading(now, ibbq2));
            addReading(IBBQ_3, new TemperatureReading(now, ibbq3));
            addReading(MEATER_PREFIX + "1 (tip)", new TemperatureReading(now, meaterTip));
            addReading(MEATER_PREFIX + "1 (ambient)", new TemperatureReading(now, meaterAmbient));
            temps.put(PROBE, probeTemp);
            temps.put(CHIP, chipTemp);
            temps.put(IBBQ_1, ibbq1);
            temps.put(IBBQ_2, ibbq2);
            temps.put(IBBQ_3, ibbq3);
            temps.put(MEATER_PREFIX + "1 (tip)", meaterTip);
            temps.put(MEATER_PREFIX + "1 (ambient)", meaterAmbient);
        }
        // Also include latest iBBQ and Meater readings from external callbacks
        if (hardwareAvailable) {
            for (String key : IBBQ_KEYS) {
                var reading = getLatestReading(key);
                if (reading != null && Duration.between(reading.timestamp(), now).toSeconds() < 10) {
                    temps.put(key, reading.temperature());
                }
            }
            for (String key : getMeaterKeys()) {
                var reading = getLatestReading(key);
                if (reading != null && Duration.between(reading.timestamp(), now).toSeconds() < 60) {
                    temps.put(key, reading.temperature());
                }
            }
        }
        logTemps(temps);
    }

    @Scheduled(every = "0.1s")
    void blowerSoftPwmTick() {
        if (!hardwareAvailable || !blowerSoftPwmEnabled) return;

        long now = System.currentTimeMillis();
        long elapsed = now - blowerCycleStart;
        if (elapsed >= BLOWER_CYCLE_MS) {
            blowerCycleStart = now;
            elapsed = 0;
        }

        long onTimeMs = BLOWER_CYCLE_MS * blowerDutyPercent / 100;
        if (onTimeMs < BLOWER_MIN_PULSE_MS) {
            // Below minimum pulse: stay off the entire cycle
            fanOutput.off();
        } else {
            if (elapsed < onTimeMs) {
                fanOutput.on();
            } else {
                fanOutput.off();
            }
        }
    }

    /**
     * Force the blower fully on or off. Disables software PWM.
     */
    public void setBlower(boolean on) {
        blowerSoftPwmEnabled = false;
        blowerForceOn = on;
        if (!hardwareAvailable) return;
        if (on) {
            fanOutput.on();
        } else {
            fanOutput.off();
        }
    }

    /**
     * Enable software PWM for the blower at the given duty cycle (1-100%).
     */
    public void setBlowerDuty(int percent) {
        if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("Blower duty must be 1-100%, got: " + percent);
        }
        blowerForceOn = false;
        blowerDutyPercent = percent;
        currentBlowerPercent = percent;
        if (!blowerSoftPwmEnabled) {
            blowerCycleStart = System.currentTimeMillis();
            blowerSoftPwmEnabled = true;
        }
    }

    /**
     * Disable the blower entirely (both force and soft PWM).
     */
    public void disableBlower() {
        blowerSoftPwmEnabled = false;
        blowerForceOn = false;
        currentBlowerPercent = 0;
        if (hardwareAvailable) fanOutput.off();
    }

    public boolean isBlowerSoftPwmEnabled() {
        return blowerSoftPwmEnabled;
    }

    public int getBlowerDutyPercent() {
        return blowerDutyPercent;
    }

    public boolean isBlowerForceOn() {
        return blowerForceOn;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public boolean isIbbqAvailable() {
        return ibbqAvailable;
    }

    public boolean isIbbqConnectionAttempted() {
        return ibbqConnectionAttempted;
    }

    public boolean isMeaterAvailable() {
        return meaterAvailable;
    }

    public boolean isMeaterConnectionAttempted() {
        return meaterConnectionAttempted;
    }

    /**
     * Returns all history keys that start with the Meater prefix.
     */
    public List<String> getMeaterKeys() {
        return history.keySet().stream()
                .filter(k -> k.startsWith(MEATER_PREFIX))
                .sorted()
                .toList();
    }

    private void connectMeater() {
        String email = meaterEmail.orElse("");
        String password = meaterPassword.orElse("");
        LOG.info("Meater Cloud: email=%s, password=%s".formatted(
                email.isBlank() ? "(not set)" : email,
                password.isBlank() ? "(not set)" : "***(" + password.length() + " chars)"));
        if (email.isBlank() || password.isBlank()) {
            LOG.info("Meater Cloud credentials not configured (set MEATER_EMAIL and MEATER_PASSWORD)");
            meaterConnectionAttempted = true;
            return;
        }
        try {
            meaterClient = new MeaterCloudClient();
            LOG.info("Meater Cloud: logging in...");
            meaterClient.login(email, password);
            meaterClient.addListener(new MeaterCloudListener() {
                @Override
                public void onDevicesUpdated(List<MeaterDevice> devices) {
                    for (int i = 0; i < devices.size(); i++) {
                        MeaterDevice dev = devices.get(i);
                        int num = i + 1;
                        String tipKey = MEATER_PREFIX + num + " (tip)";
                        String ambientKey = MEATER_PREFIX + num + " (ambient)";
                        history.putIfAbsent(tipKey, new CopyOnWriteArrayList<>());
                        history.putIfAbsent(ambientKey, new CopyOnWriteArrayList<>());
                        Instant ts = dev.updatedAt();
                        addReading(tipKey, new TemperatureReading(ts, dev.internalTemperature()));
                        addReading(ambientKey, new TemperatureReading(ts, dev.ambientTemperature()));
                    }
                }

                @Override
                public void onError(Exception error) {
                    LOG.log(Level.WARNING, "Meater Cloud poll error", error);
                }
            });
            meaterClient.startPolling(30);
            meaterAvailable = true;
            LOG.info("Meater Cloud connected, polling every 30s");
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Meater Cloud connection failed", e);
        } finally {
            meaterConnectionAttempted = true;
        }
    }

    private static final int IBBQ_SCAN_SECONDS = 10;
    private static final int IBBQ_RETRY_INTERVAL_SECONDS = 60;
    private static final int IBBQ_STALE_THRESHOLD_SECONDS = 30;

    private void connectIbbq() {
        while (true) {
            // Phase 1: connect with retries
            for (int attempt = 1; !ibbqAvailable; attempt++) {
                LOG.info("iBBQ scan attempt %d...".formatted(attempt));
                try {
                    connectIbbqOnce();
                    LOG.info("iBBQ thermometer connected on attempt %d".formatted(attempt));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "iBBQ scan attempt %d failed: %s".formatted(attempt, e.getMessage()));
                } finally {
                    ibbqConnectionAttempted = true;
                }
                if (!ibbqAvailable) {
                    try {
                        Thread.sleep(Duration.ofSeconds(IBBQ_RETRY_INTERVAL_SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            // Phase 2: monitor connection, detect data staleness
            LOG.info("iBBQ monitoring connection...");
            while (ibbqAvailable) {
                try {
                    Thread.sleep(Duration.ofSeconds(IBBQ_STALE_THRESHOLD_SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                var latest = getLatestReading(IBBQ_1);
                if (latest == null) continue;
                long ageSec = Duration.between(latest.timestamp(), Instant.now()).toSeconds();
                if (ageSec > IBBQ_STALE_THRESHOLD_SECONDS) {
                    LOG.warning("iBBQ data stale (%ds old), reconnecting...".formatted(ageSec));
                    ibbqAvailable = false;
                    if (ibbqThermometer != null) {
                        ibbqThermometer.close();
                        ibbqThermometer = null;
                    }
                }
            }
            // Loop back to phase 1
        }
    }

    private void connectIbbqOnce() throws Exception {
        ibbqThermometer = IBBQThermometer.scan(IBBQ_SCAN_SECONDS);
        ibbqThermometer.addListener(new IBBQListener() {
            @Override
            public void onTemperatureUpdate(TemperatureUpdate update) {
                for (var probe : update.probes()) {
                    if (probe.channel() < IBBQ_KEYS.length && probe.isConnected()) {
                        addReading(IBBQ_KEYS[probe.channel()],
                                new TemperatureReading(update.timestamp(), probe.temperatureCelsius()));
                    }
                }
            }
        });
        ibbqAvailable = true;
    }

    /**
     * Manually trigger an iBBQ reconnect attempt in the background.
     * No-op if already connected.
     */
    public void reconnectIbbq() {
        if (ibbqAvailable) return;
        ibbqConnectionAttempted = false;
        Thread.ofVirtual().name("ibbq-reconnect").start(() -> {
            LOG.info("Manual iBBQ reconnect requested");
            try {
                connectIbbqOnce();
                LOG.info("iBBQ thermometer connected (manual reconnect)");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Manual iBBQ reconnect failed: " + e.getMessage());
            } finally {
                ibbqConnectionAttempted = true;
            }
        });
    }

    private void prefillDummyHistory() {
        Instant now = Instant.now();
        String meaterTip = MEATER_PREFIX + "1 (tip)";
        String meaterAmbient = MEATER_PREFIX + "1 (ambient)";
        history.putIfAbsent(meaterTip, new CopyOnWriteArrayList<>());
        history.putIfAbsent(meaterAmbient, new CopyOnWriteArrayList<>());
        for (int i = MAX_HISTORY; i > 0; i--) {
            Instant ts = now.minusSeconds(i * 5L);
            double progress = (MAX_HISTORY - i) / (double) MAX_HISTORY;
            addReading(PROBE, new TemperatureReading(ts, 220.0 + Math.random() * 15 - 7.5));
            addReading(CHIP, new TemperatureReading(ts, 40.0 + Math.random() * 5 - 2.5));
            addReading(IBBQ_1, new TemperatureReading(ts, 175.0 + Math.random() * 15 - 7.5));
            addReading(IBBQ_2, new TemperatureReading(ts, 40.0 + progress * 32 + Math.random() * 4 - 2));
            addReading(IBBQ_3, new TemperatureReading(ts, 35.0 + progress * 33 + Math.random() * 4 - 2));
            addReading(meaterTip, new TemperatureReading(ts, 38.0 + progress * 36 + Math.random() * 4 - 2));
            addReading(meaterAmbient, new TemperatureReading(ts, 190.0 + Math.random() * 10 - 5));
        }
    }

    /**
     * Restore temperature history from smoker.log on startup.
     * Reads TEMPS lines, finds the most recent contiguous session (no gap > 1 hour),
     * and populates the history map.
     */
    private void restoreFromLog() {
        if (!Files.exists(LOG_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(LOG_FILE);

            // Parse all TEMPS lines with timestamps
            record ParsedEntry(LocalDateTime time, String key, double value) {}
            var entries = new ArrayList<ParsedEntry>();
            var timestamps = new ArrayList<LocalDateTime>();

            for (String line : lines) {
                String[] parts = line.split("\t");
                if (parts.length < 3 || !"TEMPS".equals(parts[1])) continue;
                LocalDateTime ts;
                try {
                    ts = LocalDateTime.parse(parts[0], TIME_FMT);
                } catch (Exception e) {
                    continue; // skip unparseable lines
                }
                timestamps.add(ts);
                for (int i = 2; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    if (eq < 0) continue;
                    String key = parts[i].substring(0, eq);
                    try {
                        double value = Double.parseDouble(parts[i].substring(eq + 1));
                        entries.add(new ParsedEntry(ts, key, value));
                    } catch (NumberFormatException e) {
                        // skip
                    }
                }
            }

            if (timestamps.isEmpty()) return;

            // Find cutoff: walk backwards, stop at first gap > 1 hour
            LocalDateTime sessionStart = timestamps.getLast();
            for (int i = timestamps.size() - 1; i > 0; i--) {
                long gapSeconds = java.time.Duration.between(timestamps.get(i - 1), timestamps.get(i)).toSeconds();
                if (gapSeconds > 3600) {
                    break;
                }
                sessionStart = timestamps.get(i - 1);
            }

            // Add entries from the session to history
            final LocalDateTime cutoff = sessionStart;
            int restored = 0;
            for (var entry : entries) {
                if (entry.time.isBefore(cutoff)) continue;
                Instant instant = entry.time.atZone(java.time.ZoneId.systemDefault()).toInstant();
                history.putIfAbsent(entry.key, new CopyOnWriteArrayList<>());
                addReading(entry.key, new TemperatureReading(instant, entry.value));
                restored++;
            }
            LOG.info("Restored %d readings from smoker.log (session start: %s)".formatted(restored, cutoff));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to restore from smoker.log", e);
        }
    }

    /**
     * Prune smoker.log: keep only lines with timestamps within the last month.
     * Lines without a parseable ISO timestamp are discarded.
     */
    private void pruneLog() {
        if (!Files.exists(LOG_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(LOG_FILE);
            LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
            var kept = new ArrayList<String>();
            for (String line : lines) {
                // Try to parse the timestamp at the start of the line
                try {
                    String tsStr = line.split("[\t ]", 2)[0];
                    LocalDateTime ts = LocalDateTime.parse(tsStr, TIME_FMT);
                    if (!ts.isBefore(oneMonthAgo)) {
                        kept.add(line);
                    }
                } catch (Exception e) {
                    // Unparseable timestamp — discard (old HH:mm:ss format or corrupt)
                }
            }
            if (kept.size() < lines.size()) {
                Files.writeString(LOG_FILE, String.join("\n", kept) + (kept.isEmpty() ? "" : "\n"));
                LOG.info("Pruned smoker.log: %d → %d lines".formatted(lines.size(), kept.size()));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to prune smoker.log", e);
        }
    }

    private void addReading(String probe, TemperatureReading reading) {
        var list = history.get(probe);
        list.add(reading);
        while (list.size() > MAX_HISTORY) {
            list.remove(0);
        }
    }

    /**
     * Append a TEMPS line to smoker.log with the given probe readings.
     */
    private void logTemps(Map<String, Double> readings) {
        if (readings.isEmpty()) return;
        try {
            Files.createDirectories(LOG_DIR);
            var sb = new StringBuilder();
            sb.append(LocalDateTime.now().format(TIME_FMT));
            sb.append("\tTEMPS");
            for (var entry : readings.entrySet()) {
                sb.append('\t').append(entry.getKey()).append('=').append("%.1f".formatted(entry.getValue()));
            }
            sb.append('\n');
            Files.writeString(LOG_FILE, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Don't let file logging failures break sensor reads
        }
    }

    public List<TemperatureReading> getHistory(String probe) {
        var list = history.get(probe);
        return list == null ? List.of() : List.copyOf(list);
    }

    public TemperatureReading getLatestReading(String probe) {
        var list = history.get(probe);
        if (list == null || list.isEmpty()) return null;
        return list.getLast();
    }

    @PreDestroy
    void cleanup() {
        if (servo != null) {
            try {
                servo.shutdown();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to clean up servo", e);
            }
        }
        if (meaterClient != null) meaterClient.close();
        if (ibbqThermometer != null) ibbqThermometer.close();
        IBBQThermometer.shutdownBle();
        if (fanOutput != null) fanOutput.off();
        if (mcp9600 != null) mcp9600.close();
        if (pi4j != null) pi4j.shutdown();
    }

    private static final double THROTTLE_MIN_ANGLE = 20;
    private static final double THROTTLE_MAX_ANGLE = 75;

    /**
     * Sets the throttle position as a percentage (0 = closed, 100 = fully open).
     */
    public void setThrottle(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Throttle must be 0-100%, got: " + percent);
        }
        currentThrottlePercent = percent;
        if (!hardwareAvailable) return;
        double angle = THROTTLE_MAX_ANGLE - percent / 100.0 * (THROTTLE_MAX_ANGLE - THROTTLE_MIN_ANGLE);
        try {
            servo.setAngle(angle);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculate the temperature rate of change for a probe over a given window.
     *
     * @param probe        the probe key
     * @param windowSeconds the time window in seconds (e.g. 30)
     * @return rate in °C per windowSeconds, or 0 if insufficient data
     */
    public double getTemperatureRate(String probe, int windowSeconds) {
        var readings = getHistory(probe);
        if (readings.size() < 2) return 0;
        TemperatureReading latest = readings.getLast();
        Instant cutoff = latest.timestamp().minusSeconds(windowSeconds);
        // Find the reading closest to the cutoff time
        TemperatureReading oldest = null;
        for (var r : readings) {
            if (!r.timestamp().isBefore(cutoff)) {
                oldest = r;
                break;
            }
        }
        if (oldest == null || oldest == latest) return 0;
        double elapsed = java.time.Duration.between(oldest.timestamp(), latest.timestamp()).toMillis() / 1000.0;
        if (elapsed < 1) return 0;
        // Normalize to windowSeconds
        return (latest.temperature() - oldest.temperature()) / elapsed * windowSeconds;
    }

    public int getThrottlePercent() {
        return currentThrottlePercent;
    }

    public int getBlowerPercent() {
        return currentBlowerPercent;
    }

    public boolean isAutomaticControlActive() {
        return automaticControlActive;
    }

    public void setAutomaticControlActive(boolean active) {
        this.automaticControlActive = active;
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(boolean enabled) {
        this.simulationMode = enabled;
    }

    /**
     * Inject a simulated temperature reading for the given probe.
     */
    public void simulateReading(String probe, double temperature) {
        history.putIfAbsent(probe, new CopyOnWriteArrayList<>());
        addReading(probe, new TemperatureReading(Instant.now(), temperature));
    }

    /**
     * Find the first Meater ambient key that has data, or null.
     */
    public String findMeaterAmbientKey() {
        for (String key : getMeaterKeys()) {
            if (key.contains("(ambient)")) {
                var list = history.get(key);
                if (list != null && !list.isEmpty()) {
                    return key;
                }
            }
        }
        return null;
    }

    public String boardName() {
        if (!hardwareAvailable) return "No hardware (dev mode)";
        return pi4j.boardInfo().getBoardModel().getName();
    }
}
