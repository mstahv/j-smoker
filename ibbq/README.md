# iBBQ — Java BLE Thermometer Library

A reusable Java library for connecting to iBBQ-protocol BLE thermometers (such as the KOBE "xBBQ") and streaming temperature data. Built on [bluez-dbus](https://github.com/hypfvieh/bluez-dbus) — works on any Linux system with BlueZ.

## Usage

### Scan and connect to the first iBBQ device in range

```java
try (var thermo = IBBQThermometer.scan(15)) { // 15 second scan timeout
    thermo.addListener(new IBBQListener() {
        @Override
        public void onTemperatureUpdate(TemperatureUpdate update) {
            for (ProbeReading probe : update.probes()) {
                if (probe.isConnected()) {
                    System.out.printf("Probe %d: %.1f°C%n",
                            probe.channel(), probe.temperatureCelsius());
                }
            }
        }

        @Override
        public void onBatteryLevel(BatteryLevel battery) {
            System.out.println("Battery: " + battery.percent() + "%");
        }
    });

    thermo.requestBatteryLevel();
    Thread.sleep(60_000); // read temperatures for 60 seconds
}
```

### Connect by known MAC address

```java
try (var thermo = IBBQThermometer.connect("AA:BB:CC:DD:EE:FF")) {
    thermo.addListener(listener);
    // ...
}
```

### Wrap an existing BluetoothDevice (caller manages DeviceManager)

```java
BluetoothDevice device = // ... obtained from your own DeviceManager
var thermo = new IBBQThermometer(device);
thermo.connectAndAuthenticate();
```

## API

### IBBQThermometer

| Method | Description |
|--------|-------------|
| `scan(int timeoutSeconds)` | Scan for and connect to the first iBBQ device found |
| `connect(String address)` | Connect to a device by BLE MAC address |
| `connectAndAuthenticate()` | Run the iBBQ handshake on an already-connected device |
| `requestBatteryLevel()` | Request battery level (arrives via listener) |
| `addListener(IBBQListener)` | Register a callback listener |
| `removeListener(IBBQListener)` | Unregister a callback listener |
| `getState()` | Current `ConnectionState` |
| `getAddress()` / `getName()` | Device BLE address and name |
| `close()` | Disconnect and clean up BLE resources |

### IBBQListener

All methods have default no-op implementations — override only what you need.

- `onTemperatureUpdate(TemperatureUpdate)` — called every ~1 second with all probe readings
- `onBatteryLevel(BatteryLevel)` — called in response to `requestBatteryLevel()`
- `onConnectionStateChanged(ConnectionState)` — DISCONNECTED, SCANNING, CONNECTING, AUTHENTICATING, READY

### Data records

- `ProbeReading(int channel, Double temperatureCelsius)` — `null` temperature means probe disconnected
- `TemperatureUpdate(Instant timestamp, List<ProbeReading> probes)` — one update per notification
- `BatteryLevel(int currentLevel, int maxLevel)` — `percent()` returns 0–100

## Building

Requires Java 25 and Maven.

```bash
# Unit tests (runs anywhere, no hardware needed):
mvn test

# Hardware tests (on Raspberry Pi with iBBQ device powered on):
mvn test -Phardware
```

## Tested hardware

- **KOBE "xBBQ"** wireless thermometer (2-probe, iBBQ protocol) — advertises as "xBBQ" over BLE
- **Raspberry Pi Zero 2 W** with built-in Bluetooth, running Raspberry Pi OS and BlueZ

The iBBQ protocol is used by many wireless BBQ thermometers from various brands (Inkbird, EasyBBQ, etc.). Devices that advertise the BLE service UUID `0000FFF0` and follow the standard iBBQ credential handshake should work.

## Dependencies

- `bluez-dbus` 0.3.2 (compile) — BlueZ BLE access via D-Bus, available on Maven Central
- `dbus-java-transport-native-unixsocket` 5.1.1 (compile) — D-Bus transport for Java 16+
- `junit-jupiter` 5.12.1 (test)
