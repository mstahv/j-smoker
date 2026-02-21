# PwmChip — Linux sysfs PWM for Java

A zero-dependency Java library for controlling hardware PWM channels via Linux sysfs (`/sys/class/pwm/`). Works on any Linux board with hardware PWM — no Pi4J or other native libraries required.

## Usage

### Raw PWM control

```java
var pwm = new PwmChip(0, 0); // chip 0, channel 0
pwm.export();
pwm.setPeriodMs(20);         // 50 Hz
pwm.setDutyCycleMs(1.5);     // 1.5 ms pulse
pwm.enable();

// ... later
pwm.disable();
pwm.unexport();
```

### Servo motor control

The library includes an abstract `Servo` base class and a ready-made `Sg90Servo` for the Waveshare SG90.

```java
var servo = new Sg90Servo(new PwmChip(0, 0));
servo.init();           // exports, sets frequency, enables
servo.setAngle(90);     // move to 90°
servo.setAngle(0);      // move to 0°
servo.shutdown();       // disables and unexports
```

#### Custom servos

Extend `Servo` to define your own pulse width range and frequency:

```java
public class MyServo extends Servo {
    public MyServo(PwmChip pwmChip) { super(pwmChip); }

    @Override protected int frequencyHz()    { return 50; }
    @Override protected double minPulseMs()  { return 1.0; }
    @Override protected double maxPulseMs()  { return 2.0; }
    @Override protected double maxAngle()    { return 270; }
}
```

## Building

Requires Java 25 and Maven.

```bash
mvn test
```

## Included servo implementations

| Class | Servo | Frequency | Pulse range | Angle range |
|-------|-------|-----------|-------------|-------------|
| `Sg90Servo` | [Waveshare SG90](https://www.waveshare.com/sg90-servo.htm) | 50 Hz | 0.5–2.4 ms | 0–180° |
