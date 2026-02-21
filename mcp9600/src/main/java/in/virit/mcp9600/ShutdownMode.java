package in.virit.mcp9600;

public enum ShutdownMode {
    NORMAL(0), SHUTDOWN(1), BURST(2);

    private final int value;

    ShutdownMode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ShutdownMode fromValue(int value) {
        for (ShutdownMode m : values()) {
            if (m.value == value) return m;
        }
        throw new IllegalArgumentException("Unknown shutdown mode value: " + value);
    }
}
