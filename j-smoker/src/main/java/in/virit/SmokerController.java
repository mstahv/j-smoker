package in.virit;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@ApplicationScoped
public class SmokerController {

    private static final Logger LOG = Logger.getLogger(SmokerController.class.getName());
    private static final Path LOG_FILE = Path.of("smoker.log");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private void log(String msg) {
        LOG.info(msg);
        fileLog(msg);
    }

    private void fileLog(String msg) {
        try {
            String line = LocalDateTime.now().format(TIME_FMT) + " " + msg + "\n";
            Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Don't let file logging failures break the controller
        }
    }

    private void logTick(double chamberTemp, double fireTemp, double fireRate, double chamberRate) {
        // Verbose to standard log
        LOG.info(("[SMOKER] state=%s chamber=%.1f fire=%.1f sp=%.1f err=%+.1f" +
                " out=%.0f T=%d%% B=%d%% fRate=%+.1f cRate=%+.1f")
                .formatted(state, chamberTemp, fireTemp, setpoint, setpoint - chamberTemp,
                        lastPidOutput, lastThrottlePercent, lastBlowerPercent,
                        fireRate, chamberRate));
        // Compact to file: time state chamber fire setpoint error output throttle blower P I D fireRate chamberRate
        fileLog(("%s %.0f %.0f %.0f %+.0f %.0f %d %d %.1f %.1f %.1f %+.1f %+.1f")
                .formatted(state, chamberTemp, fireTemp, setpoint, setpoint - chamberTemp,
                        lastPidOutput, lastThrottlePercent, lastBlowerPercent,
                        lastPTerm, lastITerm, lastDTerm, fireRate, chamberRate));
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

    // Durations for sustained condition detection (in ticks, each 5s)
    private static final int LOW_FUEL_TICKS = 24;  // 2 minutes
    private static final int WATER_PAN_TICKS = 24;  // 2 minutes

    @Inject
    SmokerHardware hardware;

    private final PidController pid = new PidController(3.0, 0.02, 0.5);

    private volatile State state = State.OFF;
    private double setpoint = 120.0;
    private Instant startTime;
    private Instant lastTickTime;

    // Sustained condition counters
    private int lowFuelCounter;
    private int waterPanCounter;

    // Heating mode: wait before engaging blower
    private static final int HEATING_BLOWER_WAIT_TICKS = 12; // 1 minute at 5s ticks
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

    // Alerts for UI consumption
    private final CopyOnWriteArrayList<String> pendingAlerts = new CopyOnWriteArrayList<>();
    private volatile boolean woodAdditionDetected;
    private volatile String chamberSource = SmokerHardware.IBBQ_1;

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
        // Immediately open throttle fully for heating
        hardware.setThrottle(100);
        fileLog("---");
        fileLog("time     state        chamb fire  sp   err   out  T%%  B%%   P     I     D     fRate  cRate");
        log("[SMOKER] STARTED: setpoint=%.1f°C, Kp=%.2f Ki=%.4f Kd=%.2f"
                .formatted(setpoint, pid.getKp(), pid.getKi(), pid.getKd()));
    }

    public void stop() {
        State prev = state;
        state = State.OFF;
        hardware.setThrottle(0);
        hardware.disableBlower();
        hardware.setAutomaticControlActive(false);
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
            this.heatingNoRiseTicks = 0;
            this.woodAdditionDetected = false;
            pid.reset();
            hardware.setAutomaticControlActive(true);
        }
        state = targetState;
        log("[SMOKER] STATE CHANGE: %s → %s (forced)".formatted(prev, targetState));
    }

    @Scheduled(every = "5s")
    void tick() {
        if (state == State.OFF) return;

        Instant now = Instant.now();
        double dtSeconds = 5.0;
        if (lastTickTime != null) {
            dtSeconds = java.time.Duration.between(lastTickTime, now).toMillis() / 1000.0;
        }
        lastTickTime = now;

        // Read sensors — iBBQ 1 primary, Meater ambient as fallback
        var chamberReading = hardware.getLatestReading(SmokerHardware.IBBQ_1);
        String activeChamberKey = SmokerHardware.IBBQ_1;
        if (chamberReading == null) {
            String meaterKey = hardware.findMeaterAmbientKey();
            if (meaterKey != null) {
                chamberReading = hardware.getLatestReading(meaterKey);
                activeChamberKey = meaterKey;
            }
        }
        var fireReading = hardware.getLatestReading(SmokerHardware.PROBE);

        if (chamberReading == null) return; // No chamber data, can't control
        chamberSource = activeChamberKey;

        double chamberTemp = chamberReading.temperature();
        double fireTemp = fireReading != null ? fireReading.temperature() : Double.NaN;
        lastChamberTemp = chamberTemp;
        lastFireTemp = fireTemp;

        double fireRate = hardware.getTemperatureRate(SmokerHardware.PROBE, RATE_WINDOW_SECONDS);
        double chamberRate = hardware.getTemperatureRate(activeChamberKey, RATE_WINDOW_SECONDS);
        lastFireRate = fireRate;
        lastChamberRate = chamberRate;

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

    private void tickHeating(double chamberTemp, double fireTemp, double fireRate, double dtSeconds) {
        // Heating: full throttle, then add blower if fire box doesn't respond
        hardware.setThrottle(100);
        lastThrottlePercent = 100;

        if (!Double.isNaN(fireTemp) && fireRate <= 1.0) {
            heatingNoRiseTicks++;
            if (heatingNoRiseTicks >= HEATING_BLOWER_WAIT_TICKS) {
                // Ramp blower: 10% per tick after wait period, max 100%
                int blowerPercent = Math.min(100, (heatingNoRiseTicks - HEATING_BLOWER_WAIT_TICKS + 1) * 10);
                hardware.setBlowerDuty(blowerPercent);
                lastBlowerPercent = blowerPercent;
            }
        } else {
            heatingNoRiseTicks = 0;
            hardware.disableBlower();
            lastBlowerPercent = 0;
        }

        lastPidOutput = lastThrottlePercent + lastBlowerPercent;
        lastError = setpoint - chamberTemp;
        lastPTerm = 0;
        lastITerm = 0;
        lastDTerm = 0;

        // Auto-transition: chamber reaches setpoint
        if (chamberTemp >= setpoint) {
            log("[SMOKER] STATE CHANGE: HEATING → SMOKING (chamber reached setpoint %.1f°C)".formatted(chamberTemp));
            state = State.SMOKING;
            pid.reset(); // Start PID fresh for smoking phase
        }
    }

    private void tickSmoking(double chamberTemp, double fireTemp, double fireRate, double chamberRate, double dtSeconds) {
        // Safety checks first

        // 1. Flame detection
        if (fireRate > flameRateThreshold) {
            log("[SMOKER] STATE CHANGE: SMOKING → FLAME_ALERT (fire_rate=%+.1f°C/30s, threshold=%.1f)"
                    .formatted(fireRate, flameRateThreshold));
            state = State.FLAME_ALERT;
            hardware.setThrottle(0);
            hardware.disableBlower();
            lastThrottlePercent = 0;
            lastBlowerPercent = 0;
            lastPidOutput = 0;
            pendingAlerts.add("Flame detected! Throttle closed.");
            return;
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
     */
    private void applyOutput(double output) {
        int throttle;
        int blower;

        if (output <= 100) {
            throttle = (int) Math.round(output);
            blower = 0;
        } else {
            throttle = 100;
            blower = (int) Math.round(output - 100);
        }

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

    public String getChamberSource() {
        return chamberSource;
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
