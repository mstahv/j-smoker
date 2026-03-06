package in.virit.meater;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolDecodingTest {

    // --- Device identification ---

    @Test
    void identifyMeaterPlusByServiceUuid() {
        var uuids = List.of("a75cc7fc-c956-488f-ac2a-2dbc08b63a04");
        assertTrue(MeaterProtocol.isMeaterProbe(uuids));
    }

    @Test
    void identifyNewerMeaterByServiceUuid() {
        var uuids = List.of("49141a23-307f-4e25-ad82-0a3f00d8b90b");
        assertTrue(MeaterProtocol.isMeaterProbe(uuids));
    }

    @Test
    void identifyMeater2PlusByServiceUuid() {
        var uuids = List.of("c9e2746c-59f1-4e54-a0dd-e1e54555cf8b");
        assertTrue(MeaterProtocol.isMeaterProbe(uuids));
    }

    @Test
    void baseStationNotIdentifiedAsProbe() {
        var uuids = List.of("0000feaf-0000-1000-8000-00805f9b34fb");
        assertFalse(MeaterProtocol.isMeaterProbe(uuids));
    }

    @Test
    void nonMeaterDeviceNotIdentified() {
        var uuids = List.of("00001800-0000-1000-8000-00805f9b34fb");
        assertFalse(MeaterProtocol.isMeaterProbe(uuids));
    }

    @Test
    void findProbeServiceUuid() {
        var uuids = List.of(
                "00001800-0000-1000-8000-00805f9b34fb",
                "a75cc7fc-c956-488f-ac2a-2dbc08b63a04");
        assertEquals("a75cc7fc-c956-488f-ac2a-2dbc08b63a04",
                MeaterProtocol.findProbeServiceUuid(uuids));
    }

    // --- UUID correctness ---

    @Test
    void characteristicUuidsAreDistinct() {
        assertNotEquals(MeaterProtocol.TEMPERATURE_UUID, MeaterProtocol.BATTERY_UUID);
    }

    @Test
    void probeServiceUuidsAreDistinct() {
        assertNotEquals(MeaterProtocol.PLUS_SERVICE_UUID, MeaterProtocol.NEWER_SERVICE_UUID);
    }

    // --- Temperature decoding ---

    @Test
    void decodeTipTemperature() {
        // tipRaw=800 → (800+8)/16.0 = 50.5°C
        // RA=48, OA=48 → delta = max(0, (48-min(48,48))*16*589/1487) = 0
        // ambient = (800 + 0 + 8) / 16.0 = 50.5
        byte[] data = leBytes(800, 48, 48);
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);

        assertEquals(1, update.probes().size());
        MeaterProbeReading probe = update.probes().getFirst();
        assertEquals(0, probe.channel());
        assertEquals(50.5, probe.tipCelsius(), 0.01);
        assertEquals(50.5, probe.ambientCelsius(), 0.01);
        assertTrue(probe.isConnected());
    }

    @Test
    void decodeAmbientHigherThanTip() {
        // tipRaw=800, RA=148, OA=48
        // delta = max(0, (148-48)*16*589/1487) = (100*16*589)/1487 = 6337
        // ambient = (800 + 6337 + 8) / 16.0 = 446.5625
        byte[] data = leBytes(800, 148, 48);
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);

        MeaterProbeReading probe = update.probes().getFirst();
        assertEquals(50.5, probe.tipCelsius(), 0.01);
        assertTrue(probe.ambientCelsius() > probe.tipCelsius());
    }

    @Test
    void decodeAmbientWhenRaBelowOa() {
        // RA=30, OA=48 → delta = max(0, (30-min(48,48))*16*589/1487) = max(0, negative) = 0
        // ambient = tip
        byte[] data = leBytes(800, 30, 48);
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);

        MeaterProbeReading probe = update.probes().getFirst();
        assertEquals(probe.tipCelsius(), probe.ambientCelsius(), 0.01);
    }

    @Test
    void disconnectedProbe() {
        byte[] data = leBytes(0, 0, 0);
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);

        MeaterProbeReading probe = update.probes().getFirst();
        assertNull(probe.tipCelsius());
        assertNull(probe.ambientCelsius());
        assertFalse(probe.isConnected());
    }

    @Test
    void tooShortDataReturnsEmptyProbes() {
        byte[] data = {0x00, 0x01};
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);
        assertTrue(update.probes().isEmpty());
    }

    @Test
    void nullDataReturnsEmptyProbes() {
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(null);
        assertTrue(update.probes().isEmpty());
    }

    // --- Battery decoding ---

    @Test
    void batteryDecoding() {
        // (5 + 5) * 10 = 100%
        byte[] data = {5, 5};
        BatteryLevel battery = MeaterProtocol.decodeBattery(data);
        assertNotNull(battery);
        assertEquals(100, battery.percent());
    }

    @Test
    void batteryClampsToMax100() {
        byte[] data = {10, 10};
        BatteryLevel battery = MeaterProtocol.decodeBattery(data);
        assertNotNull(battery);
        assertEquals(100, battery.percent());
    }

    @Test
    void batteryLow() {
        byte[] data = {1, 0};
        BatteryLevel battery = MeaterProtocol.decodeBattery(data);
        assertNotNull(battery);
        assertEquals(10, battery.percent());
    }

    @Test
    void batteryNullForEmptyData() {
        assertNull(MeaterProtocol.decodeBattery(new byte[0]));
        assertNull(MeaterProtocol.decodeBattery(null));
    }

    @Test
    void batteryTooShortData() {
        assertNull(MeaterProtocol.decodeBattery(new byte[]{5}));
    }

    // --- Timestamp ---

    @Test
    void timestampIsSet() {
        byte[] data = leBytes(800, 48, 48);
        TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);
        assertNotNull(update.timestamp());
    }

    // --- BatteryLevel record ---

    @Test
    void batteryLevelClampsAbove100() {
        assertEquals(100, new BatteryLevel(150).percent());
    }

    @Test
    void batteryLevelClampsBelowZero() {
        assertEquals(0, new BatteryLevel(-5).percent());
    }

    // --- Helper ---

    private static byte[] leBytes(int... values) {
        byte[] data = new byte[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            data[i * 2] = (byte) (values[i] & 0xFF);
            data[i * 2 + 1] = (byte) ((values[i] >> 8) & 0xFF);
        }
        return data;
    }
}
