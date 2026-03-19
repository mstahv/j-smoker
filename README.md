# J-Smoker: Automated Smoker Temperature Control System

An intelligent Java-based system for controlling and monitoring smoker temperatures using Raspberry Pi, servos, blowers, and temperature sensors.

## 📋 Overview

J-Smoker is a Java application designed to automate temperature control for smoking food. The system uses (and the used harware in the only known "production setup"):

- A blower motor for air circulation. (*centrifucal blower fan 5015 12V*, used in e.g. 3d printing setups). Controlled with a basic mosfet transistor via GPIO.
- A servo motor to control a broken chainsaw carburetor valve. ([micro servo](https://www.waveshare.com/product/robotics/motors-servos/servos/mg996r-servo.htm)) 
- Bluetooth and thermocouple temperature sensors ([amplifier](https://www.triopak.fi/fi/tuote/ADA-4101), [probe](https://www.triopak.fi/fi/tuote/UT-T05),  ibbq (Some similar to [this](https://www.alibaba.com/product-detail/Hyperbbq-AT-02-smart-wireless-thermometer_1600464854717.html), mine probably older model or different brand), [Meater](https://meater.com) (bluetooht, but conneted via cloud API)) 
- Raspberry Pi Zero 2 W as the control unit, with whopping 512 mb of ram 😎
- [Pi4J library](https://www.pi4j.com) for hardware interaction


**Note, if you want to re-build your own system based on this, you can probably quite easily change the attached electronics.*

The actuators can be used manually via web/PWA appo, but as the automation developes, it hopefully eliminates the need for manual valve adjustments and provides more consistent smoking temperatures, making the smoking process less stressful and more enjoyable.

Dashboard and for temperatures and progress of the food.

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
- **Power Supply**: Adequate power for Raspberry Pi and motors. My setup is run via 12v battery, smoking continuews even if Trump or Putin shuts down the lights.
- **Enclosure**: Protective case for electronics
- **Cooling**: Heat sinks or fans for Raspberry Pi (might be needed in warmer operation temperatures or when more load for the app)

## 💻 Software Requirements

- **Java 25**
- **Raspberry Pi OS**
- **Quarkus Framework** (lightweight Java server)
- **Pi4J Library**
- **Maven** (for dependency management)

## 🚀 Installation/Development

### Clone and Build

*Note, this you can do on your workstation, if you have Docker up and running*

```bash
git clone https://github.com/mstahv/j-smoker.git
cd j-smoker
mvn install
```

The top-level POM is an aggregator that builds all modules in the correct order — library modules first, then the main application. The modules are independent (no shared parent POM) so they can also be built individually or moved to separate repositories.

### Running the Application/Server

You can test the web UI (in *j-smoker*) also locally on your workstation, the hardware if simulated if not running on Raspberry Pi. You probably want to open in IDE and run Quarkus app from there. On CLI

```bash
cd j-smoker
# Development mode (hot reload)
./mvn quarkus:dev

# Production mode
./mvn package
java -jar target/quarkus-app/quarkus-run.jar --config config.properties
```

### Deploying

#### Prerequisites on a Server

1. **Set up Raspberry Pi** for Java execution:

   Check e.g. [Pi4J instructions](https://www.pi4j.com/getting-started/java-development-on-the-raspberry-pi-with-vsc/), although they are probably bit too extensive if you only execute Java, like I do.

2. **Enable GPIO and I2C**:
   ```bash
   sudo raspi-config
   # Navigate to: Interface Options → I2C → Enable
   # Navigate to: Interface Options → SPI → Enable
   ```

// TODO hardware PWM for servo
3. **Configure hardware PWM**:

   Add to config.txt:

   ```
   dtoverlay=pwm,pin=12,func=4
   ```
Then "just run" on the Pi.

### Deployment tips

 I suggest to use  e.g. [boot2vm](https://github.com/mstahv/boot2vm) to deploy to your Raspberry Pi. That rsyncs only the changed parts of the app, uses systemd service and a separate user for deployment. Note, you might need to add the generated user to certain groups to allow hardware access (i2c,gpio). 

If you want to expose your smoker to "interwebs" from your local private network, check e.g. Cloudflare Tunnel or ngrok as easy options.

## Project Structure

### [`j-smoker/`](j-smoker/README.md) — Main Application

Quarkus + Vaadin web application that runs on the Raspberry Pi. Provides a web UI for controlling the smoker (servo, fan) and monitoring temperatures with live-updating gauges and sparkline charts.

### [`mcp9600/`](mcp9600/README.md) — MCP9600 Java Library

A standalone, reusable Java library for the [MCP9600](https://www.microchip.com/en-us/product/mcp9600) thermocouple amplifier over I2C, built on Pi4J 4.0.0. Can be used independently in any Pi4J project.

### [`pwmchip/`](pwmchip/README.md) — Linux sysfs PWM Library

A standalone, zero-dependency Java library for controlling hardware PWM channels via Linux sysfs (`/sys/class/pwm/`). Includes an abstract `Servo` base class and a ready-made `Sg90Servo` implementation. Works on any Linux board with hardware PWM — no Pi4J required.

### [`ibbq/`](ibbq/README.md) — iBBQ BLE Thermometer Library

A standalone Java library for connecting to iBBQ-protocol BLE thermometers and streaming temperature data. Built on [bluez-dbus](https://github.com/hypfvieh/bluez-dbus) (Maven Central, no custom repositories). Tested with a KOBE "xBBQ" 2-probe wireless thermometer on Raspberry Pi.

### [`meater/`](meater/README.md) — Meater BLE Thermometer Library

A Java library for communicating with Meater BLE thermometer probes directly on Linux via BlueZ D-Bus. Currently not used: reverse engineering new newer Meater Pro
probes is not functioning properly. Using the `meater-cloud` instead.

### `meater-cloud/` — Meater Cloud API Client

A client library for reading Meater probe temperatures via the Meater cloud REST API.

## 🔌 Hardware Setup

### Servo Motor (Throttle)

- Connected via hardware PWM: sysfs `pwmchip0/pwm0` (enable with `dtoverlay=pwm,pin=12,func=4` in config.txt)
- Controlled as an SG90-compatible servo (50 Hz, 0.5–2.4 ms pulse range)
- Throttle range mapped to 20°–75° servo angle

### Blower Motor

- Controlled via GPIO **25** through a MOSFET transistor
- Software PWM with 10-second cycle for gentle on/off control

### Temperature Sensors

- **Thermocouple (MCP9600)**: I2C bus 1, address `0x67` — wired to the Pi's I2C pins (SDA/SCL)
- **iBBQ BLE thermometer**: Paired via Bluetooth, no wiring needed
- **Meater**: Connected via cloud API, no direct hardware connection

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

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a pull request

### Code Style

- Follow Java Style Guide
- Use meaningful variable and method names
- Include Javadoc comments for public methods
- Write unit tests for new functionality

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Pi4J](https://pi4j.com/)
- [Vaadin](https://vaadin.com/)
- [Quarkus](https://quarkus.io/)

## 📞 Support

For issues, questions, or suggestions:
- Open an issue on GitHub

## 🎯 Roadmap

- [ ] Machine learning for temperature prediction
- [ ] LLM-Yolo smoking mode: local LLM, instruct what you put inside and let LLM decide how it is smoked

