package in.virit.ibbq;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * iBBQ BLE protocol constants and byte encode/decode utilities.
 * Package-private so it can be unit-tested without BLE hardware.
 */
class IBBQProtocol {

    static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    static final UUID SETTINGS_RESPONSE_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    static final UUID CREDENTIALS_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    static final UUID REALTIME_UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    static final UUID SETTINGS_UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");

    static final byte[] CREDENTIALS = {
            0x21, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
            (byte) 0xb8, 0x22, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    static final byte[] REALTIME_ENABLE = {0x0B, 0x01, 0x00, 0x00, 0x00, 0x00};

    static final byte[] BATTERY_REQUEST = {0x08, 0x24, 0x00, 0x00, 0x00, 0x00};

    static final byte[] UNITS_CELSIUS = {0x02, 0x00, 0x00, 0x00, 0x00, 0x00};

    // Sentinel values for disconnected probes
    private static final short DISCONNECTED_FFFF = (short) 0xFFFF; // -1 as signed
    private static final short DISCONNECTED_8000 = (short) 0x8000; // -32768 as signed

    private IBBQProtocol() {}

    /**
     * Decode temperature notification bytes into a TemperatureUpdate.
     * Temperatures are little-endian int16, in tenths of degrees Celsius.
     */
    static TemperatureUpdate decodeTemperatures(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int probeCount = data.length / 2;
        List<ProbeReading> probes = new ArrayList<>(probeCount);
        for (int i = 0; i < probeCount; i++) {
            short raw = buf.getShort();
            Double temp = isDisconnected(raw) ? null : raw / 10.0;
            probes.add(new ProbeReading(i, temp));
        }
        return new TemperatureUpdate(Instant.now(), List.copyOf(probes));
    }

    /**
     * Decode a battery level response from the settings response characteristic.
     * Expected format: 0x24, currentLow, currentHigh, maxLow, maxHigh
     * Returns null if the data is not a battery response.
     */
    static BatteryLevel decodeBattery(byte[] data) {
        if (data.length < 5 || (data[0] & 0xFF) != 0x24) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN);
        int current = buf.getShort() & 0xFFFF;
        int max = buf.getShort() & 0xFFFF;
        return new BatteryLevel(current, max);
    }

    private static boolean isDisconnected(short raw) {
        return raw == DISCONNECTED_FFFF || raw == DISCONNECTED_8000;
    }
}
