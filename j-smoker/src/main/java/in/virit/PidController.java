package in.virit;

/**
 * Pure PID controller with derivative-on-measurement and anti-windup clamping.
 * Output range is 0.0–200.0 ("power units").
 */
public class PidController {

    private double kp;
    private double ki;
    private double kd;

    private final double outputMin;
    private final double outputMax;

    private double integralSum;
    private double previousMeasurement = Double.NaN;

    public PidController(double kp, double ki, double kd, double outputMin, double outputMax) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.outputMin = outputMin;
        this.outputMax = outputMax;
    }

    public PidController(double kp, double ki, double kd) {
        this(kp, ki, kd, 0.0, 200.0);
    }

    /**
     * Compute the PID output.
     *
     * @param setpoint    desired temperature
     * @param measurement current temperature
     * @param dtSeconds   time since last call in seconds
     * @return control output clamped to [outputMin, outputMax]
     */
    public double compute(double setpoint, double measurement, double dtSeconds) {
        if (dtSeconds <= 0) {
            return 0;
        }

        double error = setpoint - measurement;

        // P term
        double pTerm = kp * error;

        // I term with conditional integration anti-windup:
        // Don't accumulate integral when it would push output further into saturation
        double preIntegral = integralSum;
        integralSum += error * dtSeconds;
        double iTerm = ki * integralSum;

        // D term: derivative-on-measurement (not on error) to avoid setpoint kick
        double dTerm = 0;
        if (!Double.isNaN(previousMeasurement)) {
            double dMeasurement = (measurement - previousMeasurement) / dtSeconds;
            dTerm = -kd * dMeasurement; // negative because rising measurement = need less output
        }
        previousMeasurement = measurement;

        double output = pTerm + iTerm + dTerm;

        // Clamp output
        output = Math.max(outputMin, Math.min(outputMax, output));

        // Anti-windup: back-calculate integral so it stays consistent with clamped output
        if (ki != 0) {
            double maxIntegralSum = (outputMax - pTerm - dTerm) / ki;
            double minIntegralSum = (outputMin - pTerm - dTerm) / ki;
            integralSum = Math.max(minIntegralSum, Math.min(maxIntegralSum, integralSum));
        }

        return output;
    }

    /**
     * Reset the controller state (integral sum and previous measurement).
     */
    public void reset() {
        integralSum = 0;
        previousMeasurement = Double.NaN;
    }

    // Diagnostic getters for UI

    public double getLastPTerm(double setpoint, double measurement) {
        return kp * (setpoint - measurement);
    }

    public double getIntegralSum() {
        return integralSum;
    }

    public double getITerm() {
        return ki * integralSum;
    }

    public double getKp() {
        return kp;
    }

    public double getKi() {
        return ki;
    }

    public double getKd() {
        return kd;
    }

    public void setKp(double kp) {
        this.kp = kp;
    }

    public void setKi(double ki) {
        this.ki = ki;
    }

    public void setKd(double kd) {
        this.kd = kd;
    }
}
