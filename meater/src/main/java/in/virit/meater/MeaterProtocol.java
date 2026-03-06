package in.virit.meater;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class MeaterProtocol {

    // Probe service UUIDs (the actual thermometer probes)
    static final UUID PLUS_SERVICE_UUID = UUID.fromString("a75cc7fc-c956-488f-ac2a-2dbc08b63a04");
    static final UUID NEWER_SERVICE_UUID = UUID.fromString("49141a23-307f-4e25-ad82-0a3f00d8b90b");
    static final UUID MEATER2_PLUS_SERVICE_UUID = UUID.fromString("c9e2746c-59f1-4e54-a0dd-e1e54555cf8b");

    // Base station/charger service UUID (not the probe itself)
    static final UUID BASE_STATION_UUID = UUID.fromString("0000feaf-0000-1000-8000-00805f9b34fb");

    // Characteristic UUIDs (same across probe models)
    static final UUID TEMPERATURE_UUID = UUID.fromString("7edda774-045e-4bbf-909b-45d1991a2876");
    static final UUID BATTERY_UUID = UUID.fromString("2adb4877-68d8-4884-bd3c-d83853bf27b8");

    private MeaterProtocol() {}

    /**
     * Check if a device is a Meater probe by looking for known probe service UUIDs.
     */
    static boolean isMeaterProbe(List<String> serviceUuids) {
        for (String uuid : serviceUuids) {
            String lower = uuid.toLowerCase();
            if (lower.equals(PLUS_SERVICE_UUID.toString())
                    || lower.equals(NEWER_SERVICE_UUID.toString())
                    || lower.equals(MEATER2_PLUS_SERVICE_UUID.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find which service UUID is present on this device, so we can look up characteristics.
     */
    static String findProbeServiceUuid(List<String> serviceUuids) {
        for (String uuid : serviceUuids) {
            String lower = uuid.toLowerCase();
            if (lower.equals(PLUS_SERVICE_UUID.toString())) {
                return PLUS_SERVICE_UUID.toString();
            }
            if (lower.equals(NEWER_SERVICE_UUID.toString())) {
                return NEWER_SERVICE_UUID.toString();
            }
            if (lower.equals(MEATER2_PLUS_SERVICE_UUID.toString())) {
                return MEATER2_PLUS_SERVICE_UUID.toString();
            }
        }
        return null;
    }

    /**
     * Decode temperature data from the probe's temperature characteristic.
     * 6 bytes minimum — 3 little-endian uint16 values:
     * Byte 0-1: tip raw
     * Byte 2-3: RA (raw ambient component)
     * Byte 4-5: OA (offset ambient component)
     *
     * Tip formula: (tipRaw + 8) / 16.0
     * Ambient formula: (tipRaw + max(0, (RA - min(48, OA)) * 16 * 589 / 1487) + 8) / 16.0
     *
     * Note: ambient sensor does not differentiate from tip below ~40°C.
     * This is by design — Meater probes are intended for cooking where ambient > tip.
     */
    static TemperatureUpdate decodeTemperatures(byte[] data) {
        if (data == null || data.length < 6) {
            return new TemperatureUpdate(Instant.now(), List.of());
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int tipRaw = buf.getShort() & 0xFFFF;
        int ra = buf.getShort() & 0xFFFF;
        int oa = buf.getShort() & 0xFFFF;

        Double tipCelsius;
        Double ambientCelsius;

        if (tipRaw == 0) {
            tipCelsius = null;
            ambientCelsius = null;
        } else {
            tipCelsius = (tipRaw + 8) / 16.0;
            int ambientDelta = Math.max(0, (ra - Math.min(48, oa)) * 16 * 589 / 1487);
            ambientCelsius = (tipRaw + ambientDelta + 8) / 16.0;
        }

        var probe = new MeaterProbeReading(0, tipCelsius, ambientCelsius);
        return new TemperatureUpdate(Instant.now(), List.of(probe));
    }

    /**
     * Decode battery data: 2 bytes, percent = (b0 + b1) * 10.
     */
    static BatteryLevel decodeBattery(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        int percent = ((data[0] & 0xFF) + (data[1] & 0xFF)) * 10;
        return new BatteryLevel(percent);
    }
}
