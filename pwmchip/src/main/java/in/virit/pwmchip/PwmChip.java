package in.virit.pwmchip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Low-level Java API for controlling hardware PWM channels by directly writing to sysfs files.
 * This class provides direct access to PWM chip functionality without using Pi4J, but with a
 * more fine-grained API suitable for controlling e.g. servo motors.
 *
 * Based on the bash script that was originally in this file:
 *   cd /sys/class/pwm/pwmchip0
 *   echo 0 > export
 *   sleep 0.1
 *   echo 10000000 > pwm0/period
 *   echo 5000000 > pwm0/duty_cycle
 *   echo 1 > pwm0/enable
 */
public class PwmChip {

    private final int chipNumber;
    private final int channel;
    private final String basePath;
    private boolean exported = false;

    /**
     * Creates a new PWM chip controller for the specified chip and channel.
     *
     * @param chipNumber The PWM chip number (typically 0 for /sys/class/pwm/pwmchip0)
     * @param channel The PWM channel (typically 0 for pwm0)
     */
    public PwmChip(int chipNumber, int channel) {
        this.chipNumber = chipNumber;
        this.channel = channel;
        this.basePath = "/sys/class/pwm/pwmchip" + chipNumber;
    }

    /**
     * Exports the PWM channel, making it available for configuration.
     * Equivalent to: echo 0 > export
     *
     * @throws IOException if the export operation fails
     */
    public void export() throws IOException {
        if (exported) {
            return;
        }

        Path exportFile = Paths.get(basePath, "export");
        Files.writeString(exportFile, String.valueOf(channel));

        // Small delay to allow the system to create the pwmX directory
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        exported = true;
    }

    /**
     * Unexports the PWM channel, releasing it.
     * Equivalent to: echo 0 > unexport
     *
     * @throws IOException if the unexport operation fails
     */
    public void unexport() throws IOException {
        if (!exported) {
            return;
        }

        Path unexportFile = Paths.get(basePath, "unexport");
        Files.writeString(unexportFile, String.valueOf(channel));
        exported = false;
    }

    /**
     * Sets the PWM period in nanoseconds.
     * Equivalent to: echo <period_ns> > pwm0/period
     *
     * @param periodNs The period in nanoseconds
     * @throws IOException if the write operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public void setPeriod(long periodNs) throws IOException {
        ensureExported();
        Path periodFile = Paths.get(basePath, "pwm" + channel, "period");
        Files.writeString(periodFile, String.valueOf(periodNs));
    }

    /**
     * Sets the PWM period in milliseconds (converted to nanoseconds internally).
     *
     * @param periodMs The period in milliseconds
     * @throws IOException if the write operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public void setPeriodMs(double periodMs) throws IOException {
        ensureExported();
        long periodNs = (long) (periodMs * 1_000_000);
        setPeriod(periodNs);
    }

    /**
     * Sets the duty cycle in nanoseconds.
     * Equivalent to: echo <duty_ns> > pwm0/duty_cycle
     *
     * @param dutyCycleNs The duty cycle in nanoseconds
     * @throws IOException if the write operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public void setDutyCycle(long dutyCycleNs) throws IOException {
        ensureExported();
        Path dutyCycleFile = Paths.get(basePath, "pwm" + channel, "duty_cycle");
        Files.writeString(dutyCycleFile, String.valueOf(dutyCycleNs));
    }

    /**
     * Sets the duty cycle in milliseconds (converted to nanoseconds internally).
     *
     * @param dutyCycleMs The duty cycle in milliseconds
     * @throws IOException if the write operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public void setDutyCycleMs(double dutyCycleMs) throws IOException {
        ensureExported();
        long dutyCycleNs = (long) (dutyCycleMs * 1_000_000);
        setDutyCycle(dutyCycleNs);
    }

    /**
     * Enables the PWM signal.
     * Equivalent to: echo 1 > pwm0/enable
     *
     * @throws IOException if the write operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public void enable() throws IOException {
        ensureExported();
        Path enableFile = Paths.get(basePath, "pwm" + channel, "enable");
        Files.writeString(enableFile, "1");
    }

    /**
     * Disables the PWM signal.
     * Equivalent to: echo 0 > pwm0/enable
     *
     * @throws IOException if the write operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public void disable() throws IOException {
        ensureExported();
        Path enableFile = Paths.get(basePath, "pwm" + channel, "enable");
        Files.writeString(enableFile, "0");
    }

    /**
     * Checks if the PWM channel is currently enabled.
     *
     * @return true if enabled, false otherwise
     * @throws IOException if the read operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public boolean isEnabled() throws IOException {
        ensureExported();
        Path enableFile = Paths.get(basePath, "pwm" + channel, "enable");
        String content = Files.readString(enableFile).trim();
        return "1".equals(content);
    }

    /**
     * Gets the current period in nanoseconds.
     *
     * @return The current period in nanoseconds
     * @throws IOException if the read operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public long getPeriod() throws IOException {
        ensureExported();
        Path periodFile = Paths.get(basePath, "pwm" + channel, "period");
        String content = Files.readString(periodFile).trim();
        return Long.parseLong(content);
    }

    /**
     * Gets the current period in milliseconds.
     *
     * @return The current period in milliseconds
     * @throws IOException if the read operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public double getPeriodMs() throws IOException {
        ensureExported();
        return getPeriod() / 1_000_000.0;
    }

    /**
     * Gets the current duty cycle in nanoseconds.
     *
     * @return The current duty cycle in nanoseconds
     * @throws IOException if the read operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public long getDutyCycle() throws IOException {
        ensureExported();
        Path dutyCycleFile = Paths.get(basePath, "pwm" + channel, "duty_cycle");
        String content = Files.readString(dutyCycleFile).trim();
        return Long.parseLong(content);
    }

    /**
     * Gets the current duty cycle in milliseconds.
     *
     * @return The current duty cycle in milliseconds
     * @throws IOException if the read operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public double getDutyCycleMs() throws IOException {
        ensureExported();
        return getDutyCycle() / 1_000_000.0;
    }

    /**
     * Gets the current duty cycle as a percentage.
     *
     * @return The current duty cycle as a percentage (0-100)
     * @throws IOException if the read operation fails
     * @throws IllegalStateException if the channel is not exported
     */
    public double getDutyCyclePercent() throws IOException {
        ensureExported();
        long period = getPeriod();
        long dutyCycle = getDutyCycle();
        return (dutyCycle * 100.0) / period;
    }

    public int getChipNumber() {
        return chipNumber;
    }

    public int getChannel() {
        return channel;
    }

    public boolean isExported() {
        return exported;
    }

    private void ensureExported() {
        if (!exported) {
            throw new IllegalStateException("PWM channel " + channel + " on chip " + chipNumber + " is not exported. Call export() first.");
        }
    }

    /**
     * Closes the PWM channel by unexporting it.
     *
     * @throws IOException if the unexport operation fails
     */
    public void close() throws IOException {
        unexport();
    }
}
