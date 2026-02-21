package in.virit.mcp9600;

public enum AmbientResolution {
    RES_0_0625(0), RES_0_25(1);

    private final int value;

    AmbientResolution(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static AmbientResolution fromValue(int value) {
        for (AmbientResolution r : values()) {
            if (r.value == value) return r;
        }
        throw new IllegalArgumentException("Unknown ambient resolution value: " + value);
    }
}
