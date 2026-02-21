package in.virit.mcp9600;

public enum BurstSamples {
    SAMPLES_1(0), SAMPLES_2(1), SAMPLES_4(2), SAMPLES_8(3),
    SAMPLES_16(4), SAMPLES_32(5), SAMPLES_64(6), SAMPLES_128(7);

    private final int value;

    BurstSamples(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static BurstSamples fromValue(int value) {
        for (BurstSamples b : values()) {
            if (b.value == value) return b;
        }
        throw new IllegalArgumentException("Unknown burst samples value: " + value);
    }
}
