package in.virit.meater;

import java.time.Instant;
import java.util.List;

public record TemperatureUpdate(Instant timestamp, List<MeaterProbeReading> probes) {
}
