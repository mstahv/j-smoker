package in.virit.pwmchip;

/**
 * Waveshare SG90 micro servo motor.
 * <ul>
 *   <li>50 Hz PWM frequency</li>
 *   <li>0.5 ms pulse = 0°</li>
 *   <li>2.4 ms pulse = 180°</li>
 *   <li>180° rotation range</li>
 * </ul>
 */
public class Sg90Servo extends Servo {

    public Sg90Servo(PwmChip pwmChip) {
        super(pwmChip);
    }

    @Override
    protected int frequencyHz() {
        return 50;
    }

    @Override
    protected double minPulseMs() {
        return 0.5;
    }

    @Override
    protected double maxPulseMs() {
        return 2.4;
    }

    @Override
    protected double maxAngle() {
        return 180;
    }
}
