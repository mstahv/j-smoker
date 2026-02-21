package in.virit.mcp9600;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("hardware")
class Mcp9600HardwareTest {

    private static Context pi4j;
    private static Mcp9600 mcp9600;

    @BeforeAll
    static void setup() {
        pi4j = Pi4J.newAutoContext();
        mcp9600 = Mcp9600.create(pi4j);
    }

    @AfterAll
    static void teardown() {
        if (mcp9600 != null) mcp9600.close();
        if (pi4j != null) pi4j.shutdown();
    }

    @Test
    void deviceVersionIsValid() {
        DeviceVersion version = mcp9600.getDeviceVersion();
        assertEquals(0x40, version.deviceId(), "MCP9600 device ID should be 0x40");
    }

    @Test
    void hotJunctionTemperatureIsReasonable() {
        double temp = mcp9600.getHotJunctionTemperature();
        System.out.printf("Hot junction (thermocouple): %.2f °C%n", temp);
        assertTrue(temp > -10 && temp < 50, "Hot junction temperature should be room-ish: " + temp);
    }

    @Test
    void coldJunctionTemperatureIsReasonable() {
        double temp = mcp9600.getColdJunctionTemperature();
        System.out.printf("Cold junction (ambient):     %.2f °C%n", temp);
        assertTrue(temp > -10 && temp < 50, "Cold junction temperature should be room-ish: " + temp);
    }

    @Test
    void deltaTemperatureIsSmallAtRoomTemp() {
        double delta = mcp9600.getDeltaTemperature();
        System.out.printf("Delta (hot - cold):          %.2f °C%n", delta);
        assertTrue(Math.abs(delta) < 10, "Delta should be small at room temp: " + delta);
    }

    @Test
    void canReadAndSetThermocoupleType() {
        ThermocoupleType original = mcp9600.getThermocoupleType();
        try {
            mcp9600.setThermocoupleType(ThermocoupleType.J);
            assertEquals(ThermocoupleType.J, mcp9600.getThermocoupleType());

            mcp9600.setThermocoupleType(ThermocoupleType.K);
            assertEquals(ThermocoupleType.K, mcp9600.getThermocoupleType());
        } finally {
            mcp9600.setThermocoupleType(original);
        }
    }

    @Test
    void canReadAndSetFilterCoefficient() {
        FilterCoefficient original = mcp9600.getFilterCoefficient();
        try {
            mcp9600.setFilterCoefficient(FilterCoefficient.MAXIMUM);
            assertEquals(FilterCoefficient.MAXIMUM, mcp9600.getFilterCoefficient());

            mcp9600.setFilterCoefficient(FilterCoefficient.OFF);
            assertEquals(FilterCoefficient.OFF, mcp9600.getFilterCoefficient());
        } finally {
            mcp9600.setFilterCoefficient(original);
        }
    }

    @Test
    void statusIsReadable() {
        DeviceStatus status = mcp9600.getStatus();
        assertNotNull(status);
    }
}
