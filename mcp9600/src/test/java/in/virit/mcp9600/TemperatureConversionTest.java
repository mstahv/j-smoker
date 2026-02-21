package in.virit.mcp9600;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemperatureConversionTest {

    @Test
    void zeroRawIsZeroDegrees() {
        assertEquals(0.0, Mcp9600.convertRawTemperature(0x0000), 0.001);
    }

    @Test
    void positiveTemperature100C() {
        // 100°C = 100 / 0.0625 = 1600 = 0x0640
        assertEquals(100.0, Mcp9600.convertRawTemperature(0x0640), 0.001);
    }

    @Test
    void positiveTemperature25C() {
        // 25°C = 25 / 0.0625 = 400 = 0x0190
        assertEquals(25.0, Mcp9600.convertRawTemperature(0x0190), 0.001);
    }

    @Test
    void negativeTemperature() {
        // -1°C: two's complement of 16 = 0x10000 - 16 = 0xFFF0
        assertEquals(-1.0, Mcp9600.convertRawTemperature(0xFFF0), 0.001);
    }

    @Test
    void negativeTemperatureMinus40C() {
        // -40°C = -40 / 0.0625 = -640; two's complement: 0x10000 - 640 = 0xFD80
        assertEquals(-40.0, Mcp9600.convertRawTemperature(0xFD80), 0.001);
    }

    @Test
    void smallFraction() {
        // 0.0625°C = raw value 1
        assertEquals(0.0625, Mcp9600.convertRawTemperature(0x0001), 0.0001);
    }

    @Test
    void maxPositiveTemperature() {
        // 0x7FFF = 32767 * 0.0625 = 2047.9375°C
        assertEquals(2047.9375, Mcp9600.convertRawTemperature(0x7FFF), 0.001);
    }

    @Test
    void justBelowSignThreshold() {
        // 0x8000 should be interpreted as negative: -2048°C
        assertEquals(-2048.0, Mcp9600.convertRawTemperature(0x8000), 0.001);
    }
}
