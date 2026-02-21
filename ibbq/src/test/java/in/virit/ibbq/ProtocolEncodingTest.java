package in.virit.ibbq;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolEncodingTest {

    @Test
    void serviceUuidIsCorrect() {
        assertEquals(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), IBBQProtocol.SERVICE_UUID);
    }

    @Test
    void credentialBytesLength() {
        assertEquals(15, IBBQProtocol.CREDENTIALS.length);
    }

    @Test
    void credentialBytesStartAndEnd() {
        assertEquals(0x21, IBBQProtocol.CREDENTIALS[0]);
        assertEquals(0x00, IBBQProtocol.CREDENTIALS[14]);
    }

    @Test
    void credentialBytesContainKnownSequence() {
        // The credential bytes should contain the descending 0x07..0x01 sequence
        assertEquals(0x07, IBBQProtocol.CREDENTIALS[1]);
        assertEquals(0x06, IBBQProtocol.CREDENTIALS[2]);
        assertEquals(0x05, IBBQProtocol.CREDENTIALS[3]);
        assertEquals(0x04, IBBQProtocol.CREDENTIALS[4]);
        assertEquals(0x03, IBBQProtocol.CREDENTIALS[5]);
        assertEquals(0x02, IBBQProtocol.CREDENTIALS[6]);
        assertEquals(0x01, IBBQProtocol.CREDENTIALS[7]);
    }

    @Test
    void realtimeEnableLength() {
        assertEquals(6, IBBQProtocol.REALTIME_ENABLE.length);
        assertEquals(0x0B, IBBQProtocol.REALTIME_ENABLE[0]);
        assertEquals(0x01, IBBQProtocol.REALTIME_ENABLE[1]);
    }

    @Test
    void batteryRequestLength() {
        assertEquals(6, IBBQProtocol.BATTERY_REQUEST.length);
        assertEquals(0x08, IBBQProtocol.BATTERY_REQUEST[0]);
        assertEquals(0x24, IBBQProtocol.BATTERY_REQUEST[1]);
    }

    @Test
    void unitsCelsiusLength() {
        assertEquals(6, IBBQProtocol.UNITS_CELSIUS.length);
        assertEquals(0x02, IBBQProtocol.UNITS_CELSIUS[0]);
    }

    @Test
    void characteristicUuidsAreDistinct() {
        UUID[] uuids = {
                IBBQProtocol.SETTINGS_RESPONSE_UUID,
                IBBQProtocol.CREDENTIALS_UUID,
                IBBQProtocol.REALTIME_UUID,
                IBBQProtocol.SETTINGS_UUID
        };
        for (int i = 0; i < uuids.length; i++) {
            for (int j = i + 1; j < uuids.length; j++) {
                assertNotEquals(uuids[i], uuids[j],
                        "UUIDs at index %d and %d should be different".formatted(i, j));
            }
        }
    }

    @Test
    void batteryPercentClamps() {
        assertEquals(0, new BatteryLevel(0, 0).percent());
        assertEquals(100, new BatteryLevel(200, 100).percent());
        assertEquals(0, new BatteryLevel(0, 100).percent());
    }
}
