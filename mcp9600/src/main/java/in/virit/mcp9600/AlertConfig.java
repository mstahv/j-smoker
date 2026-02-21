package in.virit.mcp9600;

public record AlertConfig(
        boolean enabled,
        boolean interruptMode,
        boolean activeHigh,
        boolean risingDirection,
        boolean monitorHotJunction,
        double temperatureLimit,
        int hysteresis
) {
}
