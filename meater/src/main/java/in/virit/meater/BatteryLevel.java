package in.virit.meater;

public record BatteryLevel(int percent) {

    public BatteryLevel {
        percent = Math.clamp(percent, 0, 100);
    }
}
