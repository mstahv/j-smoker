package in.virit.ibbq;

import java.time.Instant;
import java.util.List;

/**
 * A temperature update containing readings from all probes.
 *
 * @param timestamp when the update was received
 * @param probes list of probe readings (one per channel)
 */
public record TemperatureUpdate(Instant timestamp, List<ProbeReading> probes) {
}
