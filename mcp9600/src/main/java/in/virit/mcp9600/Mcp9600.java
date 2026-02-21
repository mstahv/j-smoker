package in.virit.mcp9600;

import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

public class Mcp9600 implements AutoCloseable {

    private static final int DEFAULT_BUS = 1;
    private static final int DEFAULT_ADDRESS = 0x67;

    // Register addresses
    private static final int REG_HOT_JUNCTION = 0x00;
    private static final int REG_DELTA_TEMP = 0x01;
    private static final int REG_COLD_JUNCTION = 0x02;
    private static final int REG_STATUS = 0x04;
    private static final int REG_THERM_CFG = 0x05;
    private static final int REG_DEVICE_CONFIG = 0x06;
    private static final int REG_ALERT_CFG_BASE = 0x08;
    private static final int REG_ALERT_HYST_BASE = 0x0C;
    private static final int REG_ALERT_LIMIT_BASE = 0x10;
    private static final int REG_DEVICE_VERSION = 0x20;

    private final I2C device;
    private final boolean ownsDevice;

    /**
     * Creates an Mcp9600 using a caller-provided I2C handle.
     * The caller is responsible for closing the I2C device.
     */
    public Mcp9600(I2C device) {
        this.device = device;
        this.ownsDevice = false;
    }

    private Mcp9600(I2C device, boolean ownsDevice) {
        this.device = device;
        this.ownsDevice = ownsDevice;
    }

    /**
     * Convenience factory that creates an I2C device internally using defaults
     * (bus 1, address 0x67). The returned Mcp9600 owns and will close the device.
     */
    public static Mcp9600 create(Context pi4j) {
        I2CProvider provider = pi4j.provider("linuxfs-i2c");
        I2CConfig config = I2C.newConfigBuilder(pi4j)
                .id("MCP9600")
                .bus(DEFAULT_BUS)
                .device(DEFAULT_ADDRESS)
                .build();
        I2C i2c = provider.create(config);
        return new Mcp9600(i2c, true);
    }

    // --- Temperature reading ---

    public double getHotJunctionTemperature() {
        return convertRawTemperature(readRegister16(REG_HOT_JUNCTION));
    }

    public double getColdJunctionTemperature() {
        return convertRawTemperature(readRegister16(REG_COLD_JUNCTION));
    }

    public double getDeltaTemperature() {
        return convertRawTemperature(readRegister16(REG_DELTA_TEMP));
    }

    static double convertRawTemperature(int raw) {
        if (raw > 0x7FFF) {
            raw -= 0x10000;
        }
        return raw * 0.0625;
    }

    // --- Thermocouple configuration ---

    public ThermocoupleType getThermocoupleType() {
        int reg = readRegister8(REG_THERM_CFG);
        return ThermocoupleType.fromValue((reg >> 4) & 0x07);
    }

    public void setThermocoupleType(ThermocoupleType type) {
        int reg = readRegister8(REG_THERM_CFG);
        reg = (reg & 0x8F) | (type.value() << 4);
        writeRegister8(REG_THERM_CFG, reg);
    }

    public FilterCoefficient getFilterCoefficient() {
        int reg = readRegister8(REG_THERM_CFG);
        return FilterCoefficient.fromValue(reg & 0x07);
    }

    public void setFilterCoefficient(FilterCoefficient filter) {
        int reg = readRegister8(REG_THERM_CFG);
        reg = (reg & 0xF8) | filter.value();
        writeRegister8(REG_THERM_CFG, reg);
    }

    // --- Device configuration ---

    public ShutdownMode getShutdownMode() {
        int reg = readRegister8(REG_DEVICE_CONFIG);
        return ShutdownMode.fromValue(reg & 0x03);
    }

    public void setShutdownMode(ShutdownMode mode) {
        int reg = readRegister8(REG_DEVICE_CONFIG);
        reg = (reg & 0xFC) | mode.value();
        writeRegister8(REG_DEVICE_CONFIG, reg);
    }

    public BurstSamples getBurstSamples() {
        int reg = readRegister8(REG_DEVICE_CONFIG);
        return BurstSamples.fromValue((reg >> 2) & 0x07);
    }

    public void setBurstSamples(BurstSamples samples) {
        int reg = readRegister8(REG_DEVICE_CONFIG);
        reg = (reg & 0xE3) | (samples.value() << 2);
        writeRegister8(REG_DEVICE_CONFIG, reg);
    }

    public AmbientResolution getAmbientResolution() {
        int reg = readRegister8(REG_DEVICE_CONFIG);
        return AmbientResolution.fromValue((reg >> 7) & 0x01);
    }

    public void setAmbientResolution(AmbientResolution resolution) {
        int reg = readRegister8(REG_DEVICE_CONFIG);
        reg = (reg & 0x7F) | (resolution.value() << 7);
        writeRegister8(REG_DEVICE_CONFIG, reg);
    }

    public AdcResolution getAdcResolution() {
        int reg = readRegister8(REG_THERM_CFG);
        return AdcResolution.fromValue((reg >> 5) & 0x03);
    }

    public void setAdcResolution(AdcResolution resolution) {
        int reg = readRegister8(REG_THERM_CFG);
        reg = (reg & 0x9F) | (resolution.value() << 5);
        writeRegister8(REG_THERM_CFG, reg);
    }

    // --- Status ---

    public DeviceStatus getStatus() {
        int reg = readRegister8(REG_STATUS);
        return new DeviceStatus(
                (reg & 0x80) != 0,
                (reg & 0x40) != 0,
                (reg & 0x10) != 0,
                (reg & 0x08) != 0,
                (reg & 0x04) != 0,
                (reg & 0x02) != 0,
                (reg & 0x01) != 0
        );
    }

    public void clearStatus() {
        writeRegister8(REG_STATUS, 0x00);
    }

    // --- Device version ---

    public DeviceVersion getDeviceVersion() {
        int raw = readRegister16(REG_DEVICE_VERSION);
        return new DeviceVersion((raw >> 8) & 0xFF, raw & 0xFF);
    }

    // --- Alert configuration ---

    public void configureAlert(int alertNumber, AlertConfig config) {
        validateAlertNumber(alertNumber);
        int idx = alertNumber - 1;

        int reg = 0;
        if (config.enabled()) reg |= 0x01;
        if (config.interruptMode()) reg |= 0x02;
        if (config.activeHigh()) reg |= 0x04;
        if (config.risingDirection()) reg |= 0x08;
        if (config.monitorHotJunction()) reg |= 0x10;
        writeRegister8(REG_ALERT_CFG_BASE + idx, reg);

        writeRegister8(REG_ALERT_HYST_BASE + idx, config.hysteresis() & 0xFF);

        int rawLimit = (int) (config.temperatureLimit() / 0.0625);
        writeRegister16(REG_ALERT_LIMIT_BASE + idx, rawLimit & 0xFFFF);
    }

    public double getAlertTemperatureLimit(int alertNumber) {
        validateAlertNumber(alertNumber);
        int idx = alertNumber - 1;
        return convertRawTemperature(readRegister16(REG_ALERT_LIMIT_BASE + idx));
    }

    private void validateAlertNumber(int alertNumber) {
        if (alertNumber < 1 || alertNumber > 4) {
            throw new IllegalArgumentException("Alert number must be 1-4, got: " + alertNumber);
        }
    }

    // --- I2C low-level operations ---

    private int readRegister8(int register) {
        return device.readRegister(register) & 0xFF;
    }

    private int readRegister16(int register) {
        byte[] buf = new byte[2];
        device.readRegister(register, buf);
        return ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
    }

    private void writeRegister8(int register, int value) {
        device.writeRegister(register, (byte) value);
        settleDelay();
    }

    private void writeRegister16(int register, int value) {
        byte[] buf = new byte[2];
        buf[0] = (byte) ((value >> 8) & 0xFF);
        buf[1] = (byte) (value & 0xFF);
        device.writeRegister(register, buf);
        settleDelay();
    }

    private void settleDelay() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (ownsDevice) {
            device.close();
        }
    }
}
