package in.virit;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SmokerController {

    private static final Logger LOG = Logger.getLogger(SmokerController.class.getName());

    private void log(String msg) {
        LOG.info(msg);
        fileLog(msg);
    }

    private void fileLog(String msg) {
        try {
            Files.createDirectories(SmokerHardware.LOG_DIR);
            String line = LocalDateTime.now().format(SmokerHardware.TIME_FMT) + " " + msg + "\n";
            Files.writeString(SmokerHardware.LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Don't let file logging failures break the controller
        }
    }

    private void logTick(double chamberTemp, double fireTemp, double fireRate, double chamberRate) {
        // Read food sensor values
        String foodValues = readFoodSensorValues();

        // Verbose to standard log
        LOG.info(("[SMOKER] state=%s chamber=%.1f(%s) fire=%.1f sp=%.1f err=%+.1f" +
                " out=%.0f T=%d%% B=%d%% fRate=%+.1f cRate=%+.1f%s")
                .formatted(state, chamberTemp, activeChamberSourceKey, fireTemp, setpoint, setpoint - chamberTemp,
                        lastPidOutput, lastThrottlePercent, lastBlowerPercent,
                        fireRate, chamberRate, foodValues));
        // Compact to file
        fileLog(("%s %.0f %.0f %.0f %+.0f %.0f %d %d %.1f %.1f %.1f %+.1f %+.1f%s")
                .formatted(state, chamberTemp, fireTemp, setpoint, setpoint - chamberTemp,
                        lastPidOutput, lastThrottlePercent, lastBlowerPercent,
                        lastPTerm, lastITerm, lastDTerm, fireRate, chamberRate, foodValues));
    }

    private String readFoodSensorValues() {
        var sb = new StringBuilder();
        // iBBQ food probes
        for (String key : new String[]{SmokerHardware.IBBQ_2, SmokerHardware.IBBQ_3}) {
            var reading = hardware.getLatestReading(key);
            if (reading != null) {
                sb.append(" %s=%.1f".formatted(key.contains("2") ? "food1" : "food2", reading.temperature()));
            }
        }
        // Meater tip probes
        for (String key : hardware.getMeaterKeys()) {
            if (key.contains("(tip)")) {
                var reading = hardware.getLatestReading(key);
                if (reading != null) {
                    sb.append(" %s=%.1f".formatted(key.replace("Meater ", "m").replace(" (tip)", ""), reading.temperature()));
                }
            }
        }
        return sb.toString();
    }

    public enum State {
        OFF, HEATING, SMOKING, FLAME_ALERT, LOW_FUEL
    }

    // Rate measurement window in seconds
    private static final int RATE_WINDOW_SECONDS = 30;

    // Flame detection: fire chamber rising faster than this (°C per 30s)
    private double flameRateThreshold = 15.0;

    // Flame recovery: rate must drop below this
    private double flameRecoveryThreshold = 5.0;

    // Wood addition: fire chamber drops faster than this (°C per 30s)
    private double woodAdditionDropThreshold = -20.0;

    // Low fuel: PID output above this % of max for extended time while fire temp drops
    private double lowFuelOutputThreshold = 160.0; // 80% of 200

    // Chamber rate feed-forward: multiplier for how aggressively to cut output
    // when chamber temp is rising (°C/30s * gain = output reduction)
    private double chamberRateGain = 8.0;

    // Durations for sustained condition detection (in ticks, each 5s)
    private static final int LOW_FUEL_TICKS = 24;  // 2 minutes
    private static final int WATER_PAN_TICKS = 24;  // 2 minutes
    private static final int FLAME_ALERT_TICKS = 12; // 60 seconds sustained high fire rate

    // Simulation time acceleration (1 = real-time)
    private int simulationSpeed = 5;

    @Inject
    SmokerHardware hardware;

    @Inject
    Event<AppEvent.SetpointChanged> setpointChangedEvent;

    @Inject
    Event<AppEvent.AutoControlStateChanged> autoControlStateChangedEvent;

    private final PidController pid = new PidController(3.0, 0.03, 1.5);

    private volatile State state = State.OFF;
    private double setpoint = 120.0;
    private Instant startTime;
    private Instant lastTickTime;

    // Grace period: after restore, hold actuators steady to observe trends
    private static final long GRACE_PERIOD_SECONDS = 120;
    private Instant graceUntil;

    // Sustained condition counters
    private int lowFuelCounter;
    private int waterPanCounter;
    private int flameAlertCounter;

    // Heating mode: wait before engaging blower
    private static final int HEATING_BLOWER_WAIT_TICKS = 6; // 30s at 5s ticks
    private int heatingNoRiseTicks;

    // Diagnostics (read by UI)
    private volatile double lastError;
    private volatile double lastPTerm;
    private volatile double lastITerm;
    private volatile double lastDTerm;
    private volatile double lastPidOutput;
    private volatile int lastThrottlePercent;
    private volatile int lastBlowerPercent;
    private volatile double lastChamberTemp = Double.NaN;
    private volatile double lastFireTemp = Double.NaN;
    private volatile double lastFireRate;
    private volatile double lastChamberRate;

    // Chamber source selection
    public enum ChamberSource {
        AUTO, IBBQ, MEATER;
        @Override
        public String toString() {
            return switch (this) {
                case AUTO -> "Auto (iBBQ → Meater)";
                case IBBQ -> "iBBQ 1";
                case MEATER -> "Meater ambient";
            };
        }
    }

    private volatile ChamberSource preferredChamberSource = ChamberSource.AUTO;

    // Alerts for UI consumption
    private final CopyOnWriteArrayList<String> pendingAlerts = new CopyOnWriteArrayList<>();
    private volatile boolean woodAdditionDetected;
    private volatile String activeChamberSourceKey = SmokerHardware.IBBQ_1;

    @PostConstruct
    void init() {
        restoreFromLog();
    }

    /**
     * Restore automatic control state from smoker.log after restart.
     * If the last STARTED was not followed by a STOPPED, resume with the same setpoint.
     * The last tick line determines whether to resume in HEATING or SMOKING.
     */
    private void restoreFromLog() {
        if (!Files.exists(SmokerHardware.LOG_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(SmokerHardware.LOG_FILE);

            // Walk backwards to find the last STARTED or STOPPED
            double restoredSetpoint = -1;
            State restoredState = null;

            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (line.contains("[SMOKER] STOPPED")) {
                    // Last event was a stop — don't resume
                    LOG.info("Last control event was STOPPED, not resuming");
                    return;
                }
                if (line.contains("[SMOKER] STARTED: setpoint=")) {
                    // Extract setpoint
                    String after = line.substring(line.indexOf("setpoint=") + 9);
                    String spStr = after.split("°")[0];
                    restoredSetpoint = Double.parseDouble(spStr);
                    break;
                }
            }

            if (restoredSetpoint < 0) return; // No STARTED found

            // Find the last tick line to determine state (HEATING, SMOKING, etc.)
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                // Tick lines start with timestamp then state: "2026-04-05T10:20:35 SMOKING 78 ..."
                for (State s : new State[]{State.SMOKING, State.HEATING, State.FLAME_ALERT, State.LOW_FUEL}) {
                    if (line.contains(" " + s.name() + " ")) {
                        restoredState = s;
                        break;
                    }
                }
                if (restoredState != null) break;
            }

            if (restoredState == null) restoredState = State.SMOKING;

            // Read last throttle and blower from tick line:
            // format: "timestamp STATE chamber fire sp err out T% B% ..."
            int restoredThrottle = restoredState == State.HEATING ? 100 : 50;
            int restoredBlower = 0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (!line.contains(" " + restoredState.name() + " ")) continue;
                // Split: [0]=timestamp [1]=state [2]=chamber [3]=fire [4]=sp [5]=err [6]=out [7]=T% [8]=B%
                String afterTimestamp = line.substring(line.indexOf(' ') + 1);
                String[] parts = afterTimestamp.split("\\s+");
                if (parts.length >= 8) {
                    try {
                        restoredThrottle = Integer.parseInt(parts[6]);
                        restoredBlower = Integer.parseInt(parts[7]);
                    } catch (NumberFormatException ignored) {}
                }
                break;
            }

            // Resume with restored actuator values
            this.setpoint = restoredSetpoint;
            this.state = restoredState;
            this.startTime = Instant.now();
            this.lastTickTime = null;
            this.lowFuelCounter = 0;
            this.waterPanCounter = 0;
            this.flameAlertCounter = 0;
            this.heatingNoRiseTicks = 0;
            this.woodAdditionDetected = false;
            pid.reset();
            hardware.setAutomaticControlActive(true);
        autoControlStateChangedEvent.fire(new AppEvent.AutoControlStateChanged(true));
            hardware.setThrottle(restoredThrottle);
            lastThrottlePercent = restoredThrottle;
            if (restoredBlower > 0) {
                hardware.setBlowerDuty(restoredBlower);
            } else {
                hardware.disableBlower();
            }
            lastBlowerPercent = restoredBlower;

            // Grace period: hold these values, only observe
            this.graceUntil = Instant.now().plusSeconds(GRACE_PERIOD_SECONDS);

            log("[SMOKER] RESTORED after restart: setpoint=%.1f°C, state=%s, T=%d%%, B=%d%%, grace=%ds"
                    .formatted(restoredSetpoint, restoredState, restoredThrottle, restoredBlower, GRACE_PERIOD_SECONDS));
            pendingAlerts.add("Auto control restored: %.0f°C, %s (observing %ds)"
                    .formatted(restoredSetpoint, restoredState, GRACE_PERIOD_SECONDS));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to restore controller state from log", e);
        }
    }

    public void start(double setpointTemp) {
        this.setpoint = setpointTemp;
        this.state = State.HEATING;
        this.startTime = Instant.now();
        this.lastTickTime = null;
        this.lowFuelCounter = 0;
        this.waterPanCounter = 0;
        this.heatingNoRiseTicks = 0;
        this.woodAdditionDetected = false;
        pid.reset();
        hardware.setAutomaticControlActive(true);
        autoControlStateChangedEvent.fire(new AppEvent.AutoControlStateChanged(true));
        // Immediately open throttle fully for heating
        hardware.setThrottle(100);
        fileLog("---");
        fileLog("time     state        chamb fire  sp   err   out  T%%  B%%   P     I     D     fRate  cRate  [food sensors]");
        log("[SMOKER] STARTED: setpoint=%.1f°C, Kp=%.2f Ki=%.4f Kd=%.2f"
                .formatted(setpoint, pid.getKp(), pid.getKi(), pid.getKd()));
    }

    public void stop() {
        State prev = state;
        state = State.OFF;
        hardware.setThrottle(0);
        hardware.disableBlower();
        hardware.setAutomaticControlActive(false);
        autoControlStateChangedEvent.fire(new AppEvent.AutoControlStateChanged(false));
        lastPidOutput = 0;
        lastThrottlePercent = 0;
        lastBlowerPercent = 0;
        if (prev != State.OFF && startTime != null) {
            long totalMinutes = java.time.Duration.between(startTime, Instant.now()).toMinutes();
            log("[SMOKER] STOPPED: total_time=%dh%02dm".formatted(totalMinutes / 60, totalMinutes % 60));
        }
    }

    /**
     * Manually transition from HEATING to SMOKING.
     */
    public void transitionToSmoking() {
        if (state == State.HEATING) {
            log("[SMOKER] STATE CHANGE: HEATING → SMOKING (manual)");
            state = State.SMOKING;
        }
    }

    /**
     * Force the state machine to a specific state.
     * If forcing to OFF, calls stop(). If forcing to a non-OFF state while OFF,
     * initializes the controller first.
     */
    public void forceState(State targetState) {
        if (targetState == State.OFF) {
            stop();
            return;
        }
        State prev = state;
        if (prev == State.OFF) {
            // Initialize without changing state to HEATING — we'll set the target state directly
            this.startTime = Instant.now();
            this.lastTickTime = null;
            this.lowFuelCounter = 0;
            this.waterPanCounter = 0;
            this.flameAlertCounter = 0;
            this.heatingNoRiseTicks = 0;
            this.woodAdditionDetected = false;
            pid.reset();
            hardware.setAutomaticControlActive(true);
        autoControlStateChangedEvent.fire(new AppEvent.AutoControlStateChanged(true));
        }
        state = targetState;
        log("[SMOKER] STATE CHANGE: %s → %s (forced)".formatted(prev, targetState));
    }

    @Scheduled(every = "5s")
    void tick() {
        if (state == State.OFF) return;

        int iterations = hardware.isSimulationMode() ? simulationSpeed : 1;
        for (int iter = 0; iter < iterations && state != State.OFF; iter++) {
            tickOnce();
        }
    }

    private void tickOnce() {
        Instant now = Instant.now();
        double dtSeconds = 5.0;
        if (lastTickTime != null) {
            dtSeconds = java.time.Duration.between(lastTickTime, now).toMillis() / 1000.0;
            if (hardware.isSimulationMode()) {
                dtSeconds = 5.0; // Fixed step in simulation
            }
        }
        lastTickTime = now;

        // Read chamber sensor based on preferred source
        SmokerHardware.TemperatureReading chamberReading = null;
        String activeKey = null;

        switch (preferredChamberSource) {
            case IBBQ -> {
                chamberReading = hardware.getLatestReading(SmokerHardware.IBBQ_1);
                activeKey = SmokerHardware.IBBQ_1;
            }
            case MEATER -> {
                String meaterKey = hardware.findMeaterAmbientKey();
                if (meaterKey != null) {
                    chamberReading = hardware.getLatestReading(meaterKey);
                    activeKey = meaterKey;
                }
            }
            case AUTO -> {
                // iBBQ door sensor is faster-reacting — use as primary, Meater as fallback
                chamberReading = hardware.getLatestReading(SmokerHardware.IBBQ_1);
                activeKey = SmokerHardware.IBBQ_1;
                if (chamberReading == null) {
                    String meaterKey = hardware.findMeaterAmbientKey();
                    if (meaterKey != null) {
                        chamberReading = hardware.getLatestReading(meaterKey);
                        activeKey = meaterKey;
                    }
                }
            }
        }
        var fireReading = hardware.getLatestReading(SmokerHardware.PROBE);

        if (chamberReading == null) return; // No chamber data, can't control
        activeChamberSourceKey = activeKey;

        double chamberTemp = chamberReading.temperature();
        double fireTemp = fireReading != null ? fireReading.temperature() : Double.NaN;
        lastChamberTemp = chamberTemp;
        lastFireTemp = fireTemp;

        double fireRate = hardware.getTemperatureRate(SmokerHardware.PROBE, RATE_WINDOW_SECONDS);
        double chamberRate = hardware.getTemperatureRate(activeKey, RATE_WINDOW_SECONDS);
        lastFireRate = fireRate;
        lastChamberRate = chamberRate;

        // Grace period after restore: hold actuator values, only observe
        if (graceUntil != null) {
            if (Instant.now().isBefore(graceUntil)) {
                lastError = setpoint - chamberTemp;
                logTick(chamberTemp, fireTemp, fireRate, chamberRate);
                return;
            }
            log("[SMOKER] Grace period ended, PID taking over");
            graceUntil = null;
            pid.reset();
        }

        // State machine logic
        switch (state) {
            case HEATING -> tickHeating(chamberTemp, fireTemp, fireRate, dtSeconds);
            case SMOKING -> tickSmoking(chamberTemp, fireTemp, fireRate, chamberRate, dtSeconds);
            case FLAME_ALERT -> tickFlameAlert(fireRate);
            case LOW_FUEL -> tickLowFuel(chamberTemp, fireTemp, fireRate, dtSeconds);
            default -> {}
        }

        // Log tick — compact format to file, verbose to standard log
        logTick(chamberTemp, fireTemp, fireRate, chamberRate);
    }

    // How far ahead (in multiples of the 30s rate) to anticipate reaching setpoint
    private static final double HEATING_ANTICIPATION = 2.0; // ~60s lookahead

    private void tickHeating(double chamberTemp, double fireTemp, double fireRate, double dtSeconds) {
        double error = setpoint - chamberTemp;
        double chamberRate = hardware.getTemperatureRate(activeChamberSourceKey, RATE_WINDOW_SECONDS);

        // Early transition: if chamber + rate-based lookahead reaches setpoint,
        // switch to PID-controlled SMOKING to avoid overshoot
        double predicted = chamberTemp + Math.max(0, chamberRate) * HEATING_ANTICIPATION;
        if (predicted >= setpoint || chamberTemp >= setpoint) {
            log("[SMOKER] STATE CHANGE: HEATING → SMOKING (chamber=%.1f°C, predicted=%.1f°C, rate=%+.1f°C/30s)"
                    .formatted(chamberTemp, predicted, chamberRate));
            state = State.SMOKING;
            pid.reset();
            return;
        }

        // Scale throttle down as chamber approaches setpoint
        // Full throttle when far away, proportionally less when close
        // At 20°C away: 100%, at 5°C away: ~25%
        int throttle;
        if (error > 20) {
            throttle = 100;
        } else {
            throttle = Math.max(20, (int) Math.round(error * 100.0 / 20.0));
        }
        hardware.setThrottle(throttle);
        lastThrottlePercent = throttle;

        // Blower logic: engage if fire not rising, ramp down gradually if it is
        if (!Double.isNaN(fireTemp) && fireRate < 3.0 && error > 10) {
            heatingNoRiseTicks++;
            if (heatingNoRiseTicks >= HEATING_BLOWER_WAIT_TICKS) {
                int blowerPercent = Math.min(100, (heatingNoRiseTicks - HEATING_BLOWER_WAIT_TICKS + 1) * 8);
                hardware.setBlowerDuty(blowerPercent);
                lastBlowerPercent = blowerPercent;
            }
        } else if (lastBlowerPercent > 0) {
            // Fire is rising or close to setpoint — gradually ramp blower down
            int blowerPercent = Math.max(0, lastBlowerPercent - 5);
            if (blowerPercent > 0) {
                hardware.setBlowerDuty(blowerPercent);
            } else {
                hardware.disableBlower();
            }
            lastBlowerPercent = blowerPercent;
        }

        lastPidOutput = lastThrottlePercent + lastBlowerPercent;
        lastError = error;
        lastPTerm = 0;
        lastITerm = 0;
        lastDTerm = 0;
    }

    private void tickSmoking(double chamberTemp, double fireTemp, double fireRate, double chamberRate, double dtSeconds) {
        // Safety checks first

        // 1. Flame detection — require sustained high fire rate
        if (fireRate > flameRateThreshold) {
            double error = setpoint - chamberTemp;
            if (!Double.isNaN(fireTemp) && fireTemp < 300 && error > 10) {
                // Early heating phase: fire is building up, not a dangerous flame.
                flameAlertCounter = 0;
                log("[SMOKER] FLAME_SUPPRESSED: fire=%.0f°C(<300), error=%+.1f°C(>10), fire_rate=%+.1f°C/30s — continuing PID"
                        .formatted(fireTemp, error, fireRate));
            } else {
                flameAlertCounter++;
                if (flameAlertCounter == 1) {
                    // First tick above threshold — notify but don't trigger yet
                    pendingAlerts.add("Fire rate high (%+.1f°C/30s) — monitoring...".formatted(fireRate));
                }
                if (flameAlertCounter >= FLAME_ALERT_TICKS) {
                    log("[SMOKER] STATE CHANGE: SMOKING → FLAME_ALERT (fire_rate=%+.1f°C/30s sustained %ds)"
                            .formatted(fireRate, flameAlertCounter * 5));
                    state = State.FLAME_ALERT;
                    hardware.setThrottle(0);
                    hardware.disableBlower();
                    lastThrottlePercent = 0;
                    lastBlowerPercent = 0;
                    lastPidOutput = 0;
                    flameAlertCounter = 0;
                    pendingAlerts.add("Flame detected! Throttle closed.");
                    return;
                }
            }
        } else {
            flameAlertCounter = 0;
        }

        // 2. Wood addition detection
        if (fireRate < woodAdditionDropThreshold) {
            if (!woodAdditionDetected) {
                woodAdditionDetected = true;
                log("[SMOKER] WOOD_ADDITION: fire_temp dropped %.1f°C in 30s".formatted(Math.abs(fireRate)));
                pendingAlerts.add("Wood addition detected");
            }
        } else {
            woodAdditionDetected = false;
        }

        // 3. Low fuel detection
        double output = computePid(chamberTemp, dtSeconds);

        // Feed-forward: if chamber is rising fast, cut output proactively
        // chamberRate is °C per 30s; e.g. +4 °C/30s → subtract 4*chamberRateGain from output
        if (chamberRate > 0) {
            output -= chamberRate * chamberRateGain;
            output = Math.max(0, output);
            lastPidOutput = output;
        }

        if (output > lowFuelOutputThreshold && !Double.isNaN(fireTemp) && fireRate < -1.0) {
            lowFuelCounter++;
            if (lowFuelCounter >= LOW_FUEL_TICKS) {
                log("[SMOKER] STATE CHANGE: SMOKING → LOW_FUEL (pid_output=%.0f/200, fire_rate=%+.1f°C/30s)"
                        .formatted(output, fireRate));
                state = State.LOW_FUEL;
                pendingAlerts.add("Low fuel — add more wood!");
            }
        } else {
            lowFuelCounter = 0;
        }

        // 4. Water pan dry detection
        double error = setpoint - chamberTemp;
        if (output < 10 && error < -2.0) {
            waterPanCounter++;
            if (waterPanCounter >= WATER_PAN_TICKS) {
                log("[SMOKER] ALERT: Water pan may be dry (pid_output=%.0f/200, chamber %+.1f°C over setpoint for %ds)"
                        .formatted(output, -error, waterPanCounter * 5));
                pendingAlerts.add("Check water pan — temperature rising uncontrollably");
                waterPanCounter = 0; // Reset to avoid spamming
            }
        } else {
            waterPanCounter = 0;
        }

        applyOutput(output);
    }

    private void tickFlameAlert(double fireRate) {
        // Keep everything shut down
        lastPidOutput = 0;
        lastThrottlePercent = 0;
        lastBlowerPercent = 0;

        // Recovery: fire rate drops below recovery threshold
        if (fireRate < flameRecoveryThreshold) {
            log("[SMOKER] STATE CHANGE: FLAME_ALERT → SMOKING (fire_rate=%+.1f°C/30s, stabilized)"
                    .formatted(fireRate));
            state = State.SMOKING;
        }
    }

    private void tickLowFuel(double chamberTemp, double fireTemp, double fireRate, double dtSeconds) {
        // PID keeps trying
        double output = computePid(chamberTemp, dtSeconds);
        applyOutput(output);

        // Recovery: fire temperature starts rising
        if (fireRate > 2.0) {
            log("[SMOKER] STATE CHANGE: LOW_FUEL → SMOKING (fire_rate=%+.1f°C/30s, recovering)"
                    .formatted(fireRate));
            state = State.SMOKING;
            lowFuelCounter = 0;
        }
    }

    private double computePid(double chamberTemp, double dtSeconds) {
        double error = setpoint - chamberTemp;
        lastError = error;

        // Store P term before compute (for diagnostics)
        lastPTerm = pid.getKp() * error;
        double prevITerm = pid.getITerm();

        double output = pid.compute(setpoint, chamberTemp, dtSeconds);

        lastITerm = pid.getITerm();
        lastDTerm = output - lastPTerm - lastITerm;
        lastPidOutput = output;

        return output;
    }

    /**
     * Map PID output 0-200 to throttle (0-100) and blower (0-100).
     * Blower starts ramping at output 60 for a smooth overlap with throttle,
     * avoiding a hard cutoff when output drops below 100.
     */
    private static final double BLOWER_START = 60;
    private static final double BLOWER_RANGE = 140; // 60..200 → blower 0..100

    private void applyOutput(double output) {
        int throttle = (int) Math.round(Math.min(100, output));
        int blower = output > BLOWER_START
                ? (int) Math.round((output - BLOWER_START) * 100.0 / BLOWER_RANGE)
                : 0;

        throttle = Math.max(0, Math.min(100, throttle));
        blower = Math.max(0, Math.min(100, blower));

        hardware.setThrottle(throttle);
        if (blower > 0) {
            hardware.setBlowerDuty(blower);
        } else {
            hardware.disableBlower();
        }

        lastThrottlePercent = throttle;
        lastBlowerPercent = blower;
    }

    // --- Getters for UI ---

    public State getState() {
        return state;
    }

    public double getSetpoint() {
        return setpoint;
    }

    public void setSetpoint(double setpoint) {
        this.setpoint = setpoint;
        setpointChangedEvent.fire(new AppEvent.SetpointChanged(setpoint));
    }

    public double getLastError() {
        return lastError;
    }

    public double getLastPTerm() {
        return lastPTerm;
    }

    public double getLastITerm() {
        return lastITerm;
    }

    public double getLastDTerm() {
        return lastDTerm;
    }

    public double getLastPidOutput() {
        return lastPidOutput;
    }

    public int getLastThrottlePercent() {
        return lastThrottlePercent;
    }

    public int getLastBlowerPercent() {
        return lastBlowerPercent;
    }

    public double getLastChamberTemp() {
        return lastChamberTemp;
    }

    public double getLastFireTemp() {
        return lastFireTemp;
    }

    public double getLastFireRate() {
        return lastFireRate;
    }

    public double getLastChamberRate() {
        return lastChamberRate;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public PidController getPid() {
        return pid;
    }

    public double getFlameRateThreshold() {
        return flameRateThreshold;
    }

    public void setFlameRateThreshold(double threshold) {
        this.flameRateThreshold = threshold;
    }

    public double getFlameRecoveryThreshold() {
        return flameRecoveryThreshold;
    }

    public void setFlameRecoveryThreshold(double threshold) {
        this.flameRecoveryThreshold = threshold;
    }

    public double getWoodAdditionDropThreshold() {
        return woodAdditionDropThreshold;
    }

    public void setWoodAdditionDropThreshold(double threshold) {
        this.woodAdditionDropThreshold = threshold;
    }

    public double getLowFuelOutputThreshold() {
        return lowFuelOutputThreshold;
    }

    public void setLowFuelOutputThreshold(double threshold) {
        this.lowFuelOutputThreshold = threshold;
    }

    public double getChamberRateGain() {
        return chamberRateGain;
    }

    public void setChamberRateGain(double gain) {
        this.chamberRateGain = gain;
    }

    public int getSimulationSpeed() {
        return simulationSpeed;
    }

    public void setSimulationSpeed(int speed) {
        this.simulationSpeed = Math.max(1, speed);
    }

    public String getActiveChamberSourceKey() {
        return activeChamberSourceKey;
    }

    public ChamberSource getPreferredChamberSource() {
        return preferredChamberSource;
    }

    public void setPreferredChamberSource(ChamberSource source) {
        this.preferredChamberSource = source;
    }

    /**
     * Drain pending alerts (each alert is returned only once).
     */
    public List<String> drainAlerts() {
        if (pendingAlerts.isEmpty()) return List.of();
        var alerts = new ArrayList<>(pendingAlerts);
        pendingAlerts.clear();
        return alerts;
    }
}
