package in.virit.ibbq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolDecodingTest {

    @Test
    void decodeSingleProbeTemperature() {
        // 250 = 25.0°C in little-endian: 0xFA, 0x00
        byte[] data = {(byte) 0xFA, 0x00};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(1, update.probes().size());
        assertEquals(0, update.probes().getFirst().channel());
        assertEquals(25.0, update.probes().getFirst().temperatureCelsius(), 0.01);
        assertTrue(update.probes().getFirst().isConnected());
    }

    @Test
    void decodeTwoProbeTemperatures() {
        // Probe 0: 250 (25.0°C), Probe 1: 1000 (100.0°C)
        byte[] data = {(byte) 0xFA, 0x00, (byte) 0xE8, 0x03};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(2, update.probes().size());
        assertEquals(25.0, update.probes().get(0).temperatureCelsius(), 0.01);
        assertEquals(100.0, update.probes().get(1).temperatureCelsius(), 0.01);
    }

    @Test
    void decodeDisconnectedProbe_0xFFFF() {
        // 0xFFFF = -1 signed = disconnected
        byte[] data = {(byte) 0xFF, (byte) 0xFF};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(1, update.probes().size());
        assertNull(update.probes().getFirst().temperatureCelsius());
        assertFalse(update.probes().getFirst().isConnected());
    }

    @Test
    void decodeDisconnectedProbe_0x8000() {
        // 0x8000 = -32768 signed = disconnected
        byte[] data = {0x00, (byte) 0x80};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(1, update.probes().size());
        assertNull(update.probes().getFirst().temperatureCelsius());
        assertFalse(update.probes().getFirst().isConnected());
    }

    @Test
    void decodeMixedConnectedAndDisconnected() {
        // Probe 0: 350 (35.0°C), Probe 1: disconnected (0xFFFF)
        byte[] data = {0x5E, 0x01, (byte) 0xFF, (byte) 0xFF};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(2, update.probes().size());
        assertEquals(35.0, update.probes().get(0).temperatureCelsius(), 0.01);
        assertNull(update.probes().get(1).temperatureCelsius());
    }

    @Test
    void decodeZeroTemperature() {
        byte[] data = {0x00, 0x00};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(0.0, update.probes().getFirst().temperatureCelsius(), 0.01);
    }

    @Test
    void decodeNegativeTemperature() {
        // -50 = -5.0°C in LE int16: 0xCE, 0xFF
        byte[] data = {(byte) 0xCE, (byte) 0xFF};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);

        assertEquals(-5.0, update.probes().getFirst().temperatureCelsius(), 0.01);
    }

    @Test
    void decodeBatteryResponse() {
        // Header 0x24, current=2048 (0x0800), max=4096 (0x1000)
        byte[] data = {0x24, 0x00, 0x08, 0x00, 0x10};
        BatteryLevel battery = IBBQProtocol.decodeBattery(data);

        assertNotNull(battery);
        assertEquals(2048, battery.currentLevel());
        assertEquals(4096, battery.maxLevel());
        assertEquals(50, battery.percent());
    }

    @Test
    void decodeBatteryResponseFullCharge() {
        // current=100, max=100
        byte[] data = {0x24, 0x64, 0x00, 0x64, 0x00};
        BatteryLevel battery = IBBQProtocol.decodeBattery(data);

        assertNotNull(battery);
        assertEquals(100, battery.percent());
    }

    @Test
    void decodeBatteryReturnsNullForNonBatteryData() {
        byte[] data = {0x0B, 0x01, 0x00};
        assertNull(IBBQProtocol.decodeBattery(data));
    }

    @Test
    void decodeBatteryReturnsNullForTooShortData() {
        byte[] data = {0x24, 0x01};
        assertNull(IBBQProtocol.decodeBattery(data));
    }

    @Test
    void timestampIsSet() {
        byte[] data = {(byte) 0xFA, 0x00};
        TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);
        assertNotNull(update.timestamp());
    }
}
