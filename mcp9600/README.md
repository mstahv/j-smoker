# MCP9600 Java API

A reusable Java library for the [MCP9600](https://www.microchip.com/en-us/product/mcp9600) thermocouple amplifier, built on [Pi4J](https://pi4j.com/) 4.0.0.

## Usage

### With an existing Pi4J I2C handle

```java
I2C i2c = // ... your Pi4J I2C device
var mcp = new Mcp9600(i2c);
double temp = mcp.getHotJunctionTemperature();
```

### Standalone (convenience factory)

```java
Context pi4j = Pi4J.newAutoContext();
try (var mcp = Mcp9600.create(pi4j)) {
    double hotTemp = mcp.getHotJunctionTemperature();
    double coldTemp = mcp.getColdJunctionTemperature();
    double delta = mcp.getDeltaTemperature();
}
```

## API

### Temperature reading

- `getHotJunctionTemperature()` — thermocouple temperature in °C
- `getColdJunctionTemperature()` — ambient temperature in °C
- `getDeltaTemperature()` — hot minus cold delta in °C

### Configuration

- `get/setThermocoupleType()` — K, J, T, N, S, E, B, R
- `get/setFilterCoefficient()` — OFF through MAXIMUM (0–7)
- `get/setAdcResolution()` — 18, 16, 14, or 12 bit
- `get/setAmbientResolution()` — 0.0625°C or 0.25°C
- `get/setShutdownMode()` — NORMAL, SHUTDOWN, BURST
- `get/setBurstSamples()` — 1–128 samples

### Status and alerts

- `getStatus()` / `clearStatus()`
- `getDeviceVersion()` — device ID (expect `0x40`) and revision
- `configureAlert(int, AlertConfig)` — configure alerts 1–4
- `getAlertTemperatureLimit(int)` — read alert limit

## Building

Requires Java 25 and Maven.

```bash
# Unit tests (runs anywhere):
mvn test

# Hardware tests (on Raspberry Pi with MCP9600 on I2C bus 1, address 0x67):
mvn test -Phardware
```

## Dependencies

- `pi4j-core` 4.0.0 (compile) — consumer provides Pi4J plugins at runtime
- `junit-jupiter` 5.12.1 (test)
