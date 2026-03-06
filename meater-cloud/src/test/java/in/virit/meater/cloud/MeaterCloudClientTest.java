package in.virit.meater.cloud;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MeaterCloudClientTest {

    @Test
    void deviceRecord() {
        var device = new MeaterDevice("abc123", 55.5, 180.0, null, Instant.ofEpochSecond(1000));
        assertEquals("abc123", device.id());
        assertEquals(55.5, device.internalTemperature());
        assertEquals(180.0, device.ambientTemperature());
        assertFalse(device.isCooking());
        assertNotNull(device.updatedAt());
    }

    @Test
    void deviceWithCook() {
        var cook = new MeaterCook("cook1", "Beef Steak", "Started", 54.0, 45.0, 600, 1200);
        var device = new MeaterDevice("abc123", 45.0, 200.0, cook, Instant.now());
        assertTrue(device.isCooking());
        assertEquals("Beef Steak", device.cook().name());
        assertEquals("Started", device.cook().state());
        assertEquals(54.0, device.cook().targetTemperature());
        assertEquals(600, device.cook().timeElapsed());
        assertEquals(1200, device.cook().timeRemaining());
    }

    @Test
    void cookRecordWithNulls() {
        var cook = new MeaterCook("cook1", "Chicken", "Not Started", null, null, null, null);
        assertNull(cook.targetTemperature());
        assertNull(cook.peakTemperature());
        assertNull(cook.timeElapsed());
        assertNull(cook.timeRemaining());
    }

    @Test
    void notAuthenticatedByDefault() {
        try (var client = new MeaterCloudClient()) {
            assertFalse(client.isAuthenticated());
        }
    }

    @Test
    void getDevicesWithoutLoginThrows() {
        try (var client = new MeaterCloudClient()) {
            assertThrows(MeaterCloudException.class, client::getDevices);
        }
    }

    @Test
    void exceptionStatusCode() {
        var ex = new MeaterCloudException("rate limited", 429);
        assertEquals(429, ex.getStatusCode());
        assertEquals("rate limited", ex.getMessage());
    }
}
