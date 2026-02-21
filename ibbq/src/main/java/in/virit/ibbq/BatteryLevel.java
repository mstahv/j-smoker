package in.virit.ibbq;

/**
 * Battery level information from the iBBQ device.
 *
 * @param currentLevel current battery level (raw value)
 * @param maxLevel maximum battery level (raw value)
 */
public record BatteryLevel(int currentLevel, int maxLevel) {

    public int percent() {
        if (maxLevel <= 0) return 0;
        return Math.clamp(currentLevel * 100 / maxLevel, 0, 100);
    }
}
