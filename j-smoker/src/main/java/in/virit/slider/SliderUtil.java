package in.virit.slider;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility methods for slider components.
 * <p>
 * Copied from {@code com.vaadin.flow.component.slider.SliderUtil} which is
 * package-private.
 */
class SliderUtil {

    private SliderUtil() {
    }

    static double clampToMinMax(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    static double snapToStep(double value, double min, double max,
            double step) {
        BigDecimal minBd = BigDecimal.valueOf(min);
        BigDecimal maxBd = BigDecimal.valueOf(max);
        BigDecimal stepBd = BigDecimal.valueOf(step);
        BigDecimal valueBd = BigDecimal.valueOf(value);

        BigDecimal stepsFromMin = valueBd.subtract(minBd).divide(stepBd, 0,
                RoundingMode.HALF_UP);

        return minBd.add(stepsFromMin.multiply(stepBd)).min(maxBd)
                .doubleValue();
    }
}
