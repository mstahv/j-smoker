package in.virit.slider;

import java.util.Map;
import java.util.Optional;

import com.vaadin.flow.component.HasAriaLabel;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.function.SerializableFunction;

/**
 * A slider component that supports arbitrary {@link Number} types as its value.
 * <p>
 * While the standard {@link com.vaadin.flow.component.slider.Slider} is fixed
 * to {@link Double} values, this component can work with {@link Integer},
 * {@link Long}, or any other {@link Number} subtype, making it easier to bind
 * to existing APIs that use specific number types.
 * <p>
 * For standard JDK number types, just pass the class:
 *
 * <pre>
 * var intSlider = new NumberSlider&lt;&gt;(Integer.class);
 * var longSlider = new NumberSlider&lt;&gt;("Offset", Long.class, 0, 1000);
 * </pre>
 *
 * @param <T>
 *            the number type for the slider value
 */
@Tag("vaadin-slider")
@NpmPackage(value = "@vaadin/slider", version = "25.1.0-beta1")
@JsModule("@vaadin/slider/src/vaadin-slider.js")
public class NumberSlider<T extends Number>
        extends SliderBase<NumberSlider<T>, T> implements HasAriaLabel {

    @SuppressWarnings("unchecked")
    private static final Map<Class<? extends Number>, SerializableFunction<Double, ?>> CONVERTERS = Map.of(
            Integer.class, (SerializableFunction<Double, Integer>) Double::intValue,
            Long.class, (SerializableFunction<Double, Long>) Double::longValue,
            Float.class, (SerializableFunction<Double, Float>) Double::floatValue,
            Short.class, (SerializableFunction<Double, Short>) Double::shortValue,
            Byte.class, (SerializableFunction<Double, Byte>) Double::byteValue,
            Double.class, (SerializableFunction<Double, Double>) d -> d);

    private final SerializableFunction<Double, T> fromDouble;

    /**
     * Creates a slider with the given number type and default range 0-100.
     * Supports {@link Integer}, {@link Long}, {@link Double}, {@link Float},
     * {@link Short} and {@link Byte}.
     *
     * @param type
     *            the number type class, e.g. {@code Integer.class}
     */
    public NumberSlider(Class<T> type) {
        this(type, 0, 100);
    }

    /**
     * Creates a slider with the given number type and range.
     *
     * @param type
     *            the number type class
     * @param min
     *            the minimum value
     * @param max
     *            the maximum value
     */
    public NumberSlider(Class<T> type, double min, double max) {
        this(min, max, resolveConverter(type), Number::doubleValue);
    }

    /**
     * Creates a slider with a label, number type and range.
     *
     * @param label
     *            the label text
     * @param type
     *            the number type class
     * @param min
     *            the minimum value
     * @param max
     *            the maximum value
     */
    public NumberSlider(String label, Class<T> type, double min, double max) {
        this(type, min, max);
        setLabel(label);
    }

    /**
     * Creates a slider with the given range and custom type converters. Use
     * this for number types not covered by the built-in class-based
     * constructors.
     *
     * @param min
     *            the minimum value
     * @param max
     *            the maximum value
     * @param fromDouble
     *            converts the web component's Double to the target type
     * @param toDouble
     *            converts the target type back to Double
     */
    public NumberSlider(double min, double max,
            SerializableFunction<Double, T> fromDouble,
            SerializableFunction<T, Double> toDouble) {
        super(min, max, Double.class, fromDouble, toDouble);
        this.fromDouble = fromDouble;
        // Re-initialize: the first clear() from super ran before fromDouble
        // was assigned, so it was a no-op. Now we can properly set the value.
        clear();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Number> SerializableFunction<Double, T> resolveConverter(
            Class<T> type) {
        SerializableFunction<Double, ?> converter = CONVERTERS.get(type);
        if (converter == null) {
            throw new IllegalArgumentException(
                    "Unsupported number type: " + type.getName()
                            + ". Use the converter constructor for custom types.");
        }
        return (SerializableFunction<Double, T>) converter;
    }

    // -- Typed min / max / step accessors --

    public T getMin() {
        return fromDouble.apply(getMinDouble());
    }

    public void setMin(T min) {
        setMinDouble(min.doubleValue());
    }

    public T getMax() {
        return fromDouble.apply(getMaxDouble());
    }

    public void setMax(T max) {
        setMaxDouble(max.doubleValue());
    }

    public T getStep() {
        return fromDouble.apply(getStepDouble());
    }

    public void setStep(T step) {
        setStepDouble(step.doubleValue());
    }

    // -- SliderBase abstract methods --

    @Override
    public void clear() {
        // Guard: fromDouble is null during the super constructor call
        if (fromDouble != null) {
            setValue(fromDouble.apply(getMinDouble()));
        }
    }

    @Override
    protected boolean hasValidValue() {
        Double value = getElement().getProperty("value", 0.0);
        if (value == null || fromDouble == null) {
            return false;
        }
        T typed = fromDouble.apply(value);
        return isValueWithinMinMax(typed) && isValueAlignedWithStep(typed);
    }

    @Override
    protected boolean isValueWithinMinMax(T value) {
        double d = value.doubleValue();
        double min = getMinDouble();
        double max = getMaxDouble();
        if (min > max) {
            return false;
        }
        return d == SliderUtil.clampToMinMax(d, min, max);
    }

    @Override
    protected boolean isValueAlignedWithStep(T value) {
        double d = value.doubleValue();
        double min = getMinDouble();
        double max = getMaxDouble();
        if (min > max) {
            return false;
        }
        return d == SliderUtil.snapToStep(d, min, max, getStepDouble());
    }

    // -- HasAriaLabel --

    @Override
    public void setAriaLabel(String ariaLabel) {
        getElement().setProperty("accessibleName", ariaLabel);
    }

    @Override
    public Optional<String> getAriaLabel() {
        return Optional.ofNullable(getElement().getProperty("accessibleName"));
    }

    @Override
    public void setAriaLabelledBy(String ariaLabelledBy) {
        getElement().setProperty("accessibleNameRef", ariaLabelledBy);
    }

    @Override
    public Optional<String> getAriaLabelledBy() {
        return Optional
                .ofNullable(getElement().getProperty("accessibleNameRef"));
    }
}
