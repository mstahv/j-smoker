package in.virit.ibbq;

/**
 * A single probe temperature reading.
 *
 * @param channel 0-based probe channel index
 * @param temperatureCelsius temperature in Celsius, or null if the probe is disconnected
 */
public record ProbeReading(int channel, Double temperatureCelsius) {

    public boolean isConnected() {
        return temperatureCelsius != null;
    }
}
