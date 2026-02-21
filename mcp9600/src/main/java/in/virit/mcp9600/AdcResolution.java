package in.virit.mcp9600;

public enum AdcResolution {
    BITS_18(0), BITS_16(1), BITS_14(2), BITS_12(3);

    private final int value;

    AdcResolution(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static AdcResolution fromValue(int value) {
        for (AdcResolution r : values()) {
            if (r.value == value) return r;
        }
        throw new IllegalArgumentException("Unknown ADC resolution value: " + value);
    }
}
