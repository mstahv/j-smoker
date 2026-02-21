package in.virit.mcp9600;

public record DeviceStatus(
        boolean burstComplete,
        boolean temperatureUpdated,
        boolean inputRange,
        boolean alert4,
        boolean alert3,
        boolean alert2,
        boolean alert1
) {
}
