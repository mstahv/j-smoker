package in.virit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PidControllerTest {

    @Test
    void pTermReactsToPositiveError() {
        var pid = new PidController(2.0, 0, 0);
        // setpoint=100, measurement=90, error=+10 → P = 2.0 * 10 = 20
        double output = pid.compute(100, 90, 5);
        assertEquals(20.0, output, 0.01);
    }

    @Test
    void pTermReactsToNegativeError() {
        var pid = new PidController(2.0, 0, 0);
        // setpoint=100, measurement=110, error=-10 → P = 2.0 * -10 = -20 → clamped to 0
        double output = pid.compute(100, 110, 5);
        assertEquals(0.0, output, 0.01);
    }

    @Test
    void iTermAccumulatesOverTime() {
        var pid = new PidController(0, 0.1, 0);
        // First tick: error=10, dt=5 → integral=50 → I = 0.1 * 50 = 5
        double out1 = pid.compute(100, 90, 5);
        assertEquals(5.0, out1, 0.01);

        // Second tick: error=10, dt=5 → integral=100 → I = 0.1 * 100 = 10
        double out2 = pid.compute(100, 90, 5);
        assertEquals(10.0, out2, 0.01);
    }

    @Test
    void antiWindupClampsIntegral() {
        var pid = new PidController(0, 1.0, 0); // High Ki to trigger saturation quickly
        // Large error, many ticks → should clamp at 200
        for (int i = 0; i < 100; i++) {
            pid.compute(300, 100, 5); // error=200
        }
        double output = pid.compute(300, 100, 5);
        assertEquals(200.0, output, 0.01);

        // After saturation, integral should be clamped.
        // With small error (10) and clamped integral, the output should settle near max
        // but not exceed it. The key property is that output stays at max, not beyond.
        double recovered = pid.compute(300, 290, 5); // error=10
        assertTrue(recovered <= 200.0, "Output should not exceed max");
        assertTrue(recovered >= 0.0, "Output should not be negative");
    }

    @Test
    void outputClampedToMinMax() {
        var pid = new PidController(100, 0, 0); // Very high Kp
        // Large positive error
        double out1 = pid.compute(200, 0, 5);
        assertEquals(200.0, out1, 0.01);

        // Large negative error
        var pid2 = new PidController(100, 0, 0);
        double out2 = pid2.compute(0, 200, 5);
        assertEquals(0.0, out2, 0.01);
    }

    @Test
    void customOutputRange() {
        var pid = new PidController(1.0, 0, 0, 10, 50);
        // error=5 → P=5 → clamped to 10 (min)
        double out1 = pid.compute(100, 95, 5);
        assertEquals(10.0, out1, 0.01);

        // error=30 → P=30 → within range
        double out2 = pid.compute(100, 70, 5);
        assertEquals(30.0, out2, 0.01);

        // error=100 → P=100 → clamped to 50 (max)
        var pid2 = new PidController(1.0, 0, 0, 10, 50);
        double out3 = pid2.compute(200, 100, 5);
        assertEquals(50.0, out3, 0.01);
    }

    @Test
    void derivativeOnMeasurementNoSpike() {
        var pid = new PidController(0, 0, 10.0);

        // First call at measurement=90, no derivative yet
        double out1 = pid.compute(100, 90, 5);
        assertEquals(0.0, out1, 0.01); // No previous measurement

        // Measurement stays same → derivative = 0
        double out2 = pid.compute(100, 90, 5);
        assertEquals(0.0, out2, 0.01);

        // Now change setpoint dramatically (100→200) but measurement stays at 90
        // Derivative-on-measurement means no spike from setpoint change
        double out3 = pid.compute(200, 90, 5);
        assertEquals(0.0, out3, 0.01); // D term is 0 because measurement didn't change
    }

    @Test
    void derivativeOnMeasurementReactsToMeasurementChange() {
        var pid = new PidController(0, 0, 10.0);

        // Initialize with measurement=90
        pid.compute(100, 90, 5);

        // Measurement rises by 5 in 5 seconds → rate = 1°/s → D = -10 * 1 = -10 → clamped to 0
        double out = pid.compute(100, 95, 5);
        assertEquals(0.0, out, 0.01); // Negative D term clamped to 0

        // Measurement drops by 5 in 5 seconds → rate = -1°/s → D = -10 * -1 = +10
        double out2 = pid.compute(100, 90, 5);
        assertEquals(10.0, out2, 0.01);
    }

    @Test
    void resetClearsState() {
        var pid = new PidController(0, 0.1, 0);
        pid.compute(100, 90, 5); // Build up integral
        pid.compute(100, 90, 5);
        assertTrue(pid.getIntegralSum() > 0);

        pid.reset();
        assertEquals(0.0, pid.getIntegralSum(), 0.01);

        // After reset, first derivative should be 0 (no previous measurement)
        var pid2 = new PidController(0, 0, 10.0);
        pid2.compute(100, 90, 5);
        pid2.reset();
        double out = pid2.compute(100, 95, 5);
        assertEquals(0.0, out, 0.01); // No previous measurement after reset
    }

    @Test
    void zeroOrNegativeDtReturnsZero() {
        var pid = new PidController(2.0, 0.1, 0.5);
        assertEquals(0.0, pid.compute(100, 90, 0));
        assertEquals(0.0, pid.compute(100, 90, -1));
    }

    @Test
    void fullPidConvergence() {
        // Simulate a simple system where PID should converge
        var pid = new PidController(2.0, 0.02, 0.5);
        double setpoint = 120;
        double measurement = 80;
        double dt = 5;

        // Run many iterations; output should stabilize
        double lastOutput = 0;
        for (int i = 0; i < 200; i++) {
            double output = pid.compute(setpoint, measurement, dt);
            // Simulate slow system response
            measurement += (output / 200.0) * 2 - 0.5; // slight cooling
            lastOutput = output;
        }

        // Measurement should be close to setpoint (slow system, generous tolerance)
        assertEquals(setpoint, measurement, 10.0);
    }

    @Test
    void gettersWork() {
        var pid = new PidController(2.0, 0.02, 0.5);
        assertEquals(2.0, pid.getKp());
        assertEquals(0.02, pid.getKi());
        assertEquals(0.5, pid.getKd());

        pid.setKp(3.0);
        pid.setKi(0.05);
        pid.setKd(1.0);
        assertEquals(3.0, pid.getKp());
        assertEquals(0.05, pid.getKi());
        assertEquals(1.0, pid.getKd());
    }
}
