package in.virit;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared storage for food probe target temperatures.
 * Visible across all browser sessions.
 * Persists to smoker.log and restores on boot if set today.
 */
@ApplicationScoped
public class FoodTargets {

    private static final Logger LOG = Logger.getLogger(FoodTargets.class.getName());
    private final Map<String, Double> targets = new ConcurrentHashMap<>();

    @Inject
    Event<AppEvent.FoodTargetsChanged> foodTargetsChangedEvent;

    @PostConstruct
    void init() {
        restoreFromLog();
    }

    public Double getTarget(String probeKey) {
        return targets.get(probeKey);
    }

    public void setTarget(String probeKey, Double temperature) {
        if (temperature == null || temperature <= 0) {
            targets.remove(probeKey);
        } else {
            targets.put(probeKey, temperature);
        }
        persist();
        foodTargetsChangedEvent.fire(new AppEvent.FoodTargetsChanged());
    }

    private void persist() {
        try {
            Files.createDirectories(SmokerHardware.LOG_DIR);
            var sb = new StringBuilder();
            sb.append(LocalDateTime.now().format(SmokerHardware.TIME_FMT));
            sb.append("\tFOOD_TARGETS");
            for (var entry : targets.entrySet()) {
                sb.append('\t').append(entry.getKey()).append('=').append("%.1f".formatted(entry.getValue()));
            }
            sb.append('\n');
            Files.writeString(SmokerHardware.LOG_FILE, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to persist food targets", e);
        }
    }

    /**
     * Restore food targets from the last FOOD_TARGETS line in smoker.log,
     * but only if it was written today.
     */
    private void restoreFromLog() {
        if (!Files.exists(SmokerHardware.LOG_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(SmokerHardware.LOG_FILE);
            String lastTargetLine = null;
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).contains("\tFOOD_TARGETS\t")) {
                    lastTargetLine = lines.get(i);
                    break;
                }
            }
            if (lastTargetLine == null) return;

            // Check if from today
            String timestamp = lastTargetLine.split("\t")[0];
            LocalDateTime ts = LocalDateTime.parse(timestamp, SmokerHardware.TIME_FMT);
            if (!ts.toLocalDate().equals(LocalDate.now())) return;

            // Parse: timestamp\tFOOD_TARGETS\tkey=value\tkey=value...
            String[] parts = lastTargetLine.split("\t");
            int restored = 0;
            for (int i = 2; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq < 0) continue;
                String key = parts[i].substring(0, eq);
                try {
                    double value = Double.parseDouble(parts[i].substring(eq + 1));
                    targets.put(key, value);
                    restored++;
                } catch (NumberFormatException ignored) {}
            }
            if (restored > 0) {
                LOG.info("Restored %d food target(s) from log (%s)".formatted(restored, timestamp));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to restore food targets from log", e);
        }
    }
}
