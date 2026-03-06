# Meater BLE Thermometer Library

A Java library for communicating with **Meater** BLE thermometer probes directly on Linux via BlueZ D-Bus.

## Limitations

- **Base stations must be unplugged.** Meater probes only allow one BLE connection at a time. If a base station (charger/repeater) is connected to the probe, the probe will not advertise and cannot be discovered by this library. Remove the base station's batteries or unplug it before scanning.
- **Only directly advertising probes are supported.** This library connects directly to the probe via BLE — it cannot read data through a base station. Only probes that advertise as "MEATER" (visible in `bluetoothctl scan`) can be found.
- **Ambient temperature does not differentiate below ~40°C.** The Meater probe's ambient sensor only reports a value distinct from the tip temperature when the ambient is above approximately 40°C. Below that threshold, ambient equals tip. This is a firmware limitation — the probe is designed for cooking where oven temperature exceeds meat temperature.
- **Newer Meater Pro model was not detected in testing.** During development, only the Meater Plus probe (service UUID `a75cc7fc-...`) was successfully discovered and connected. A newer Meater Pro probe did not advertise even with its base station unplugged. The library includes UUIDs for newer models (`49141a23-...`, `c9e2746c-...`) but these are untested.
- **Linux only.** Requires BlueZ D-Bus, so only works on Linux systems (e.g., Raspberry Pi).

For an alternative that works with all Meater models and doesn't require base station removal, see the `meater-cloud` module which uses the Meater Cloud REST API.

## Supported Devices

| Model | Probes | Identification |
|---|---|---|
| Meater Plus | 1 (tip + ambient) | Service UUID `a75cc7fc-...` or name "MEATER" |
| Meater 2 Plus | 1 (tip + ambient) | Service UUID `c9e2746c-...` (untested) |
| Newer models | unknown | Service UUID `49141a23-...` (untested) |

## Usage

```java
// Scan for any Meater device (15 second timeout)
try (MeaterThermometer thermo = MeaterThermometer.scan(15)) {
    thermo.addListener(new MeaterListener() {
        @Override
        public void onTemperatureUpdate(TemperatureUpdate update) {
            for (MeaterProbeReading probe : update.probes()) {
                if (probe.isConnected()) {
                    System.out.printf("Probe %d: tip=%.1f°C ambient=%.1f°C%n",
                        probe.channel(), probe.tipCelsius(), probe.ambientCelsius());
                }
            }
        }

        @Override
        public void onBatteryLevel(BatteryLevel battery) {
            System.out.println("Battery: " + battery.percent() + "%");
        }
    });

    thermo.readBatteryLevel();

    // Temperature updates arrive via polling (1 second interval)
    Thread.sleep(30_000);
}
```

## Building

```bash
mvn clean install
```

## Testing

Unit tests (no hardware):
```bash
mvn test
```

Hardware integration tests (requires Meater probe in range with base station unplugged):
```bash
mvn test -Phardware
```

## Requirements

- Java 25+
- Linux with BlueZ
- Meater Plus or compatible probe (base station must be unplugged)

## License

Apache License 2.0
