package in.virit;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import in.virit.mcp9600.Mcp9600;
import in.virit.pwmchip.PwmChip;
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
    static int HZ_50 = 50;

    private Context pi4j;

    final double dutyCycle0 = 0.5;
    final double dutyCycle180 = 2.4;
    private PwmChip pwmChip;
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

            pwmChip = new PwmChip(0, 0);
            pwmChip.export();
            pwmChip.setPeriodMs(1000 / HZ_50);
            pwmChip.setDutyCycleMs(dutyCycle0);
            pwmChip.enable();

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
        if (pwmChip != null) {
            try {
                pwmChip.disable();
                pwmChip.unexport();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to clean up PWM", e);
            }
        }
        if (mcp9600 != null) mcp9600.close();
        if (pi4j != null) pi4j.shutdown();
    }

    public void setServoAngle(double servoAngle) {
        if (!hardwareAvailable) return;
        if (servoAngle < 0 || servoAngle > 180) {
            throw new IllegalArgumentException("0-180° only");
        }
        try {
            pwmChip.export();
            pwmChip.setPeriodMs(1000 / HZ_50);
            double dutyCycleMs = dutyCycle0 + servoAngle / 180 * (dutyCycle180 - dutyCycle0);
            pwmChip.setDutyCycleMs(dutyCycleMs);
            System.out.println(dutyCycleMs + " " + pwmChip.getPeriodMs());
            pwmChip.enable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFan(boolean on) {
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
