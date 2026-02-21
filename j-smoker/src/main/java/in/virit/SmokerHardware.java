package in.virit;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
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

    static int FAN_GPIO = 25;

    private Context pi4j;

    private Servo servo;
    private DigitalOutput fanOutput;
    private Mcp9600 mcp9600;
    private boolean hardwareAvailable;

    public record TemperatureReading(Instant timestamp, double temperature) {}

    private final Map<String, CopyOnWriteArrayList<TemperatureReading>> history = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        history.put(PROBE, new CopyOnWriteArrayList<>());
        history.put(CHIP, new CopyOnWriteArrayList<>());

        if (!new java.io.File("/dev/i2c-1").exists()) {
            LOG.info("Hardware not detected, running in UI-only mode");
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
    }

    @Scheduled(every = "5s")
    void readTemperatures() {
        System.out.println("Reading temperatures...");
        Instant now = Instant.now();
        double probe = hardwareAvailable ? mcp9600.getHotJunctionTemperature() : 225.0 + Math.random() * 10 - 5;
        double chip = hardwareAvailable ? mcp9600.getColdJunctionTemperature() : 42.0 + Math.random() * 2 - 1;
        addReading(PROBE, new TemperatureReading(now, probe));
        addReading(CHIP, new TemperatureReading(now, chip));
    }

    private void prefillDummyHistory() {
        Instant now = Instant.now();
        for (int i = MAX_HISTORY; i > 0; i--) {
            Instant ts = now.minusSeconds(i * 5L);
            addReading(PROBE, new TemperatureReading(ts, 220.0 + Math.random() * 15 - 7.5));
            addReading(CHIP, new TemperatureReading(ts, 40.0 + Math.random() * 5 - 2.5));
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
        double angle = THROTTLE_MIN_ANGLE + percent / 100.0 * (THROTTLE_MAX_ANGLE - THROTTLE_MIN_ANGLE);
        try {
            servo.setAngle(angle);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setBlower(boolean on) {
        if (!hardwareAvailable) return;
        if (on) {
            fanOutput.on();
        } else {
            fanOutput.off();
        }
    }

    public String boardName() {
        if (!hardwareAvailable) return "No hardware (dev mode)";
        return pi4j.boardInfo().getBoardModel().getName();
    }
}
