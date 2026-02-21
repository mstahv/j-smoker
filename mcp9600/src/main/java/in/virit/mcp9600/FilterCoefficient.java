package in.virit.mcp9600;

public enum FilterCoefficient {
    OFF(0), MINIMUM(1), LEVEL_2(2), LEVEL_3(3), LEVEL_4(4), LEVEL_5(5), LEVEL_6(6), MAXIMUM(7);

    private final int value;

    FilterCoefficient(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static FilterCoefficient fromValue(int value) {
        for (FilterCoefficient f : values()) {
            if (f.value == value) return f;
        }
        throw new IllegalArgumentException("Unknown filter coefficient value: " + value);
    }
}
