package in.virit.meater;

public record MeaterProbeReading(int channel, Double tipCelsius, Double ambientCelsius) {

    public boolean isConnected() {
        return tipCelsius != null;
    }
}
