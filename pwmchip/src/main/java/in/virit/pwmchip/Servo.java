package in.virit.pwmchip;

import java.io.IOException;

/**
 * Abstract base for PWM-controlled servo motors.
 * Subclasses define the servo's pulse width range, frequency, and angle limits.
 */
public abstract class Servo {

    private final PwmChip pwmChip;

    protected Servo(PwmChip pwmChip) {
        this.pwmChip = pwmChip;
    }

    /** PWM frequency in Hz (typically 50). */
    protected abstract int frequencyHz();

    /** Pulse width in ms corresponding to minimum angle. */
    protected abstract double minPulseMs();

    /** Pulse width in ms corresponding to maximum angle. */
    protected abstract double maxPulseMs();

    /** Maximum rotation angle in degrees. */
    protected abstract double maxAngle();

    /**
     * Initializes the PWM channel for servo operation.
     */
    public void init() throws IOException {
        pwmChip.export();
        pwmChip.setPeriodMs(1000.0 / frequencyHz());
        setAngle(0);
        pwmChip.enable();
    }

    /**
     * Sets the servo to the given angle.
     *
     * @param degrees angle in degrees, from 0 to {@link #maxAngle()}
     */
    public void setAngle(double degrees) throws IOException {
        if (degrees < 0 || degrees > maxAngle()) {
            throw new IllegalArgumentException("Angle must be 0-" + (int) maxAngle() + "°, got: " + degrees);
        }
        double pulseMs = minPulseMs() + degrees / maxAngle() * (maxPulseMs() - minPulseMs());
        pwmChip.setDutyCycleMs(pulseMs);
    }

    /**
     * Disables the PWM signal and releases the channel.
     */
    public void shutdown() throws IOException {
        pwmChip.disable();
        pwmChip.unexport();
    }
}
