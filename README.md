# J-Smoker: Automated Smoker Temperature Control System

An intelligent Java-based system for controlling and monitoring smoker temperatures using Raspberry Pi, servos, blowers, and temperature sensors.

## 📋 Overview

J-Smoker is a Java application designed to automate temperature control for smoking food. The system uses:

- A blower motor for air circulation
- A servo motor to control the carburetor valve
- Bluetooth and thermocouple temperature sensors
- Raspberry Pi Zero 2 W as the control unit
- Pi4J library for hardware interaction

This automation eliminates the need for manual valve adjustments and provides more consistent smoking temperatures, making the smoking process less stressful and more enjoyable.

## 🔥 Features

- **Automatic Temperature Control**: Maintains target temperature by adjusting airflow
- **Dual Sensor Monitoring**: Uses both Bluetooth and thermocouple sensors for redundancy
- **Servo Control**: Precisely adjusts the carburetor valve position
- **Blower Management**: Controls air circulation for optimal combustion
- **Real-time Monitoring**: Continuous temperature tracking and logging
- **Alert System**: Notifications for temperature deviations

## 🛠️ Hardware Requirements

### Main Components
- **Raspberry Pi Zero 2 W** (or compatible Raspberry Pi model)
- **MG996R Servo Motor** (or similar) for carburetor control
- **Blower Motor** for air circulation
- **Bluetooth Temperature Sensor** for wireless monitoring
- **Thermocouple Sensor** for direct temperature measurement
- **Old Motor Saw Carburetor** (modified for servo control)

### Optional Components
- **Power Supply**: Adequate power for Raspberry Pi and motors
- **Enclosure**: Protective case for electronics
- **Cooling**: Heat sinks or fans for Raspberry Pi

### Wiring Diagram

```
Raspberry Pi GPIO → Servo Motor (PWM control)
Raspberry Pi GPIO → Blower Motor (PWM/Relay control)
Raspberry Pi Bluetooth → Bluetooth Temperature Sensor
Raspberry Pi GPIO → Thermocouple Interface (ADC)
```

## 💻 Software Requirements

- **Java 25** (OpenJDK recommended)
- **Raspberry Pi OS** (32-bit or 64-bit)
- **Quarkus Framework** (lightweight Java server)
- **Pi4J Library** (v4.0-SNAPSHOT - requires local build)
- **Maven** (for dependency management)
- **Git** (for version control)

## 🚀 Installation

### Prerequisites

1. **Set up Raspberry Pi**:
   ```bash
   sudo apt update && sudo apt upgrade -y
   sudo apt install openjdk-11-jdk maven git -y
   ```

2. **Enable GPIO and I2C**:
   ```bash
   sudo raspi-config
   # Navigate to: Interface Options → I2C → Enable
   # Navigate to: Interface Options → SPI → Enable
   ```

### Clone the Repository

```bash
git clone https://github.com/yourusername/j-smoker.git
cd j-smoker
```

### Build the Project

```bash
mvn clean package
```

### Install Dependencies

```bash
# Install Quarkus CLI (optional but recommended)
curl -Ls https://sh.jbang.dev | bash -s - trust add https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/
mvn wrapper:wrapper

# Install Pi4J v4.0-SNAPSHOT (requires local build)
# First, clone and build Pi4J from source:
git clone https://github.com/Pi4J/pi4j-v4.git
cd pi4j-v4
mvn clean install -DskipTests

# Then install the required dependencies:
mvn dependency:get -Dartifact=com.pi4j:pi4j-core:4.0-SNAPSHOT
mvn dependency:get -Dartifact=com.pi4j:pi4j-plugin-raspberrypi:4.0-SNAPSHOT
mvn dependency:get -Dartifact=io.quarkus:quarkus-bom:3.9.0
```

## 🎛️ Configuration

Create a `config.properties` file in the project root:

```properties
# Temperature Settings
target.temperature=225
max.temperature=275
min.temperature=180

# Hardware Settings
servo.pin=18
blower.pin=17
thermocouple.pin=4

# Control Parameters
pid.kp=1.0
pid.ki=0.1
pid.kd=0.01
control.interval=5

# Sensor Configuration
bluetooth.sensor.name=Smoker-Probe
bluetooth.sensor.address=XX:XX:XX:XX:XX:XX
```

## 🔧 Usage

### Running the Application

```bash
# Development mode (hot reload)
./mvnw quarkus:dev

# Production mode
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar --config config.properties
```

### Command Line Options

```
Usage: java -jar quarkus-run.jar [options]

Options:
  --config <file>      Path to configuration file (default: config.properties)
  --debug              Enable debug logging
  --simulate           Run in simulation mode (no hardware control)
  --help               Show this help message
  --quarkus.http.port  Set HTTP port (default: 8080)
```

### Web Interface (Vaadin)

The web interface is built with **Vaadin** and accessible at `http://<raspberry-pi-ip>:8080` for:
- Real-time temperature monitoring with interactive charts
- Manual control override for servo and blower
- Configuration adjustments with immediate feedback
- Historical data visualization and export
- Responsive design that works on desktop and mobile devices

## Project Structure

The repository contains three modules:

### `j-smoker/` — Main Application

Quarkus + Vaadin web application that runs on the Raspberry Pi. Provides a web UI for controlling the smoker (servo, fan) and monitoring temperatures with live-updating gauges and sparkline charts.

### `mcp9600/` — MCP9600 Java Library

A standalone, reusable Java library for the [MCP9600](https://www.microchip.com/en-us/product/mcp9600) thermocouple amplifier over I2C, built on Pi4J 4.0.0. Can be used independently in any Pi4J project. See [`mcp9600/README.md`](mcp9600/README.md) for API details and usage examples.

### `pwmchip/` — Linux sysfs PWM Library

A standalone, zero-dependency Java library for controlling hardware PWM channels via Linux sysfs (`/sys/class/pwm/`). Includes an abstract `Servo` base class and a ready-made `Sg90Servo` implementation. Works on any Linux board with hardware PWM — no Pi4J required. See [`pwmchip/README.md`](pwmchip/README.md) for API details.

### `ibbq/` — iBBQ BLE Thermometer Library

A standalone Java library for connecting to iBBQ-protocol BLE thermometers and streaming temperature data. Built on [bluez-dbus](https://github.com/hypfvieh/bluez-dbus) (Maven Central, no custom repositories). Tested with a KOBE "xBBQ" 2-probe wireless thermometer on Raspberry Pi. See [`ibbq/README.md`](ibbq/README.md) for API details and usage examples.

## 🔌 Hardware Setup

### Servo Motor Connection

1. Connect servo signal wire to GPIO pin 18 (PWM0)
2. Connect power (5V) and ground to appropriate pins
3. Ensure proper power supply for servo (may need external power)

### Blower Motor Connection

1. Use a transistor or relay module for motor control
2. Connect control pin to GPIO pin 17
3. Ensure proper power supply for blower motor

### Temperature Sensors

1. **Bluetooth Sensor**: Pair with Raspberry Pi via Bluetooth settings
2. **Thermocouple**: Connect to ADC input on GPIO pin 4

## 🤖 Control Algorithm

The system uses a PID (Proportional-Integral-Derivative) controller:

- **Proportional**: Responds to current temperature error
- **Integral**: Corrects for accumulated past errors
- **Derivative**: Predicts future temperature trends

The servo position and blower speed are adjusted based on the PID output to maintain target temperature.

## 📊 Monitoring and Logging

Logs are stored in `logs/j-smoker.log` with the following information:
- Timestamped temperature readings
- Control actions taken
- System events and warnings
- Error conditions

## 🛠️ Development

### Building from Source

```bash
# Build with Quarkus
./mvnw clean package

# Build native executable (for Raspberry Pi)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

### Running Tests

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify
```

### Quarkus Development Tools

```bash
# Add extensions
./mvnw quarkus:add-extension -Dextensions="vaadin,rest,jdbc-postgresql"

# Create native image
./mvnw package -Dnative

# Build container image
./mvnw package -Dquarkus.container-image.build=true
```

### IDE Setup

Recommended IDEs:
- IntelliJ IDEA with Java and Maven plugins
- Eclipse with m2e plugin
- VS Code with Java Extension Pack

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a pull request

### Code Style

- Follow Google Java Style Guide
- Use meaningful variable and method names
- Include Javadoc comments for public methods
- Write unit tests for new functionality

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Pi4J](https://pi4j.com/) for excellent Raspberry Pi Java support
- [Waveshare](https://www.waveshare.com/) for quality hardware components
- The BBQ community for inspiration and feedback

## 📞 Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Join our community forum
- Check the wiki for detailed documentation

## 🎯 Roadmap

- [ ] Mobile app for remote monitoring
- [ ] Cloud integration for data logging
- [ ] Machine learning for temperature prediction
- [ ] Multi-zone temperature control
- [ ] Voice control integration

---

© 2023 J-Smoker Project. All rights reserved.
