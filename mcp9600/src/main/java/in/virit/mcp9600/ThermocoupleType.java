package in.virit.mcp9600;

public enum ThermocoupleType {
    K(0), J(1), T(2), N(3), S(4), E(5), B(6), R(7);

    private final int value;

    ThermocoupleType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ThermocoupleType fromValue(int value) {
        for (ThermocoupleType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown thermocouple type value: " + value);
    }
}
