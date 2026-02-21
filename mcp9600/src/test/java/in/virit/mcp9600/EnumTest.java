package in.virit.mcp9600;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTest {

    @Test
    void thermocoupleTypeRoundTrip() {
        for (ThermocoupleType t : ThermocoupleType.values()) {
            assertEquals(t, ThermocoupleType.fromValue(t.value()));
        }
    }

    @Test
    void filterCoefficientRoundTrip() {
        for (FilterCoefficient f : FilterCoefficient.values()) {
            assertEquals(f, FilterCoefficient.fromValue(f.value()));
        }
    }

    @Test
    void shutdownModeRoundTrip() {
        for (ShutdownMode m : ShutdownMode.values()) {
            assertEquals(m, ShutdownMode.fromValue(m.value()));
        }
    }

    @Test
    void burstSamplesRoundTrip() {
        for (BurstSamples b : BurstSamples.values()) {
            assertEquals(b, BurstSamples.fromValue(b.value()));
        }
    }

    @Test
    void ambientResolutionRoundTrip() {
        for (AmbientResolution r : AmbientResolution.values()) {
            assertEquals(r, AmbientResolution.fromValue(r.value()));
        }
    }

    @Test
    void adcResolutionRoundTrip() {
        for (AdcResolution r : AdcResolution.values()) {
            assertEquals(r, AdcResolution.fromValue(r.value()));
        }
    }
}
