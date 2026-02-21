package in.virit;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import in.virit.ibbq.IBBQListener;
import in.virit.ibbq.IBBQThermometer;
import in.virit.ibbq.TemperatureUpdate;
import in.virit.mcp9600.Mcp9600;
import in.virit.pwmchip.PwmChip;
import in.virit.pwmchip.Servo;
import in.virit.pwmchip.Sg90Servo;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SmokerHardware {

    private static final Logger LOG = Logger.getLogger(SmokerHardware.class.getName());
    private static final int MAX_HISTORY = 120; // 10 minutes at 5s interval

    public static final String PROBE = "Probe";
    public static final String CHIP = "Chip";
    public static final String IBBQ_1 = "iBBQ 1 (chamber)";
    public static final String IBBQ_2 = "iBBQ 2 (food)";
    public static final String IBBQ_3 = "iBBQ 3 (food)";
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

    // Blower state
    private boolean blowerForceOn;
    private boolean blowerSoftPwmEnabled;
    private int blowerDutyPercent; // 1-100
    private long blowerCycleStart;

    public record TemperatureReading(Instant timestamp, double temperature) {}

    private final Map<String, CopyOnWriteArrayList<TemperatureReading>> history = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        history.put(PROBE, new CopyOnWriteArrayList<>());
        history.put(CHIP, new CopyOnWriteArrayList<>());
        history.put(IBBQ_1, new CopyOnWriteArrayList<>());
        history.put(IBBQ_2, new CopyOnWriteArrayList<>());
        history.put(IBBQ_3, new CopyOnWriteArrayList<>());

        if (!new java.io.File("/dev/i2c-1").exists()) {
            LOG.info("Hardware not detected, running in UI-only mode with fake data");
            devMode = true;
            prefillDummyHistory();
            return;
        }

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
        Instant now = Instant.now();
        if (hardwareAvailable) {
            addReading(PROBE, new TemperatureReading(now, mcp9600.getHotJunctionTemperature()));
            addReading(CHIP, new TemperatureReading(now, mcp9600.getColdJunctionTemperature()));
        } else if (devMode) {
            addReading(PROBE, new TemperatureReading(now, 225.0 + Math.random() * 10 - 5));
            addReading(CHIP, new TemperatureReading(now, 42.0 + Math.random() * 2 - 1));
            addReading(IBBQ_1, new TemperatureReading(now, 180.0 + Math.random() * 10 - 5));
            addReading(IBBQ_2, new TemperatureReading(now, 72.0 + Math.random() * 4 - 2));
            addReading(IBBQ_3, new TemperatureReading(now, 68.0 + Math.random() * 4 - 2));
        }
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

    private void connectIbbq() {
        try {
            ibbqThermometer = IBBQThermometer.scan(10);
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
            LOG.info("iBBQ thermometer connected");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "iBBQ thermometer not found", e);
        } finally {
            ibbqConnectionAttempted = true;
        }
    }

    private void prefillDummyHistory() {
        Instant now = Instant.now();
        for (int i = MAX_HISTORY; i > 0; i--) {
            Instant ts = now.minusSeconds(i * 5L);
            addReading(PROBE, new TemperatureReading(ts, 220.0 + Math.random() * 15 - 7.5));
            addReading(CHIP, new TemperatureReading(ts, 40.0 + Math.random() * 5 - 2.5));
            addReading(IBBQ_1, new TemperatureReading(ts, 175.0 + Math.random() * 15 - 7.5));
            addReading(IBBQ_2, new TemperatureReading(ts, 70.0 + Math.random() * 8 - 4));
            addReading(IBBQ_3, new TemperatureReading(ts, 65.0 + Math.random() * 8 - 4));
        }
    }

    private void addReading(String probe, TemperatureReading reading) {
        var list = history.get(probe);
        list.add(reading);
        while (list.size() > MAX_HISTORY) {
            list.remove(0);
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
        if (ibbqThermometer != null) ibbqThermometer.close();
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
        if (!hardwareAvailable) return;
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Throttle must be 0-100%, got: " + percent);
        }
        double angle = THROTTLE_MAX_ANGLE - percent / 100.0 * (THROTTLE_MAX_ANGLE - THROTTLE_MIN_ANGLE);
        try {
            servo.setAngle(angle);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String boardName() {
        if (!hardwareAvailable) return "No hardware (dev mode)";
        return pi4j.boardInfo().getBoardModel().getName();
    }
}
