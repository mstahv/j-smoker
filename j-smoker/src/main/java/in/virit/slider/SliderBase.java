package in.virit.slider;

import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.KeyNotifier;
import com.vaadin.flow.component.shared.HasValidationProperties;
import com.vaadin.flow.component.shared.InputField;
import com.vaadin.flow.data.value.HasValueChangeMode;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.signals.Signal;

/**
 * Abstract base class for slider components.
 * <p>
 * Copied from {@code com.vaadin.flow.component.slider.SliderBase} which is
 * package-private. The experimental feature-flag check has been removed.
 *
 * @param <TComponent>
 *            the component type
 * @param <TValue>
 *            the value type
 */
abstract class SliderBase<TComponent extends SliderBase<TComponent, TValue>, TValue>
        extends AbstractSinglePropertyField<TComponent, TValue> implements
        InputField<ComponentValueChangeEvent<TComponent, TValue>, TValue>,
        HasValidationProperties, HasValueChangeMode, Focusable<TComponent>,
        KeyNotifier {

    private static final double DEFAULT_STEP = 1.0;

    private ValueChangeMode currentMode;

    private int valueChangeTimeout = DEFAULT_CHANGE_TIMEOUT;

    private boolean consistencyCheckPending = false;

    protected <TPresentation> SliderBase(double min, double max,
            Class<TPresentation> presentationType,
            SerializableFunction<TPresentation, TValue> presentationToModel,
            SerializableFunction<TValue, TPresentation> modelToPresentation) {
        super("value", null, presentationType, presentationToModel,
                modelToPresentation);

        getElement().setProperty("manualValidation", true);
        setInvalid(false);

        setMinDouble(min);
        setMaxDouble(max);
        setStepDouble(DEFAULT_STEP);
        clear();

        setValueChangeMode(ValueChangeMode.ON_CHANGE);
    }

    double getMinDouble() {
        return getElement().getProperty("min", 0.0);
    }

    double getMaxDouble() {
        return getElement().getProperty("max", 100.0);
    }

    double getStepDouble() {
        return getElement().getProperty("step", 1.0);
    }

    void setMinDouble(double min) {
        getElement().setProperty("min", min);
        schedulePropertyConsistencyCheck();
    }

    void setMaxDouble(double max) {
        getElement().setProperty("max", max);
        schedulePropertyConsistencyCheck();
    }

    void setStepDouble(double step) {
        if (step <= 0) {
            throw new IllegalArgumentException(
                    "The step must be greater than 0.");
        }
        getElement().setProperty("step", step);
        schedulePropertyConsistencyCheck();
    }

    public void bindMin(Signal<Double> signal) {
        getElement().bindProperty("min", signal, null);
    }

    public void bindMax(Signal<Double> signal) {
        getElement().bindProperty("max", signal, null);
    }

    public void bindStep(Signal<Double> signal) {
        getElement().bindProperty("step", signal, null);
    }

    public void setValueAlwaysVisible(boolean valueAlwaysVisible) {
        getElement().setProperty("valueAlwaysVisible", valueAlwaysVisible);
    }

    public boolean isValueAlwaysVisible() {
        return getElement().getProperty("valueAlwaysVisible", false);
    }

    public void setMinMaxVisible(boolean minMaxVisible) {
        getElement().setProperty("minMaxVisible", minMaxVisible);
    }

    public boolean isMinMaxVisible() {
        return getElement().getProperty("minMaxVisible", false);
    }

    @Override
    public ValueChangeMode getValueChangeMode() {
        return currentMode;
    }

    @Override
    public void setValueChangeMode(ValueChangeMode valueChangeMode) {
        currentMode = valueChangeMode;
        setSynchronizedEvent(
                ValueChangeMode.eventForMode(valueChangeMode, "value-changed"));
        applyChangeTimeout();
    }

    @Override
    public void setValueChangeTimeout(int valueChangeTimeout) {
        this.valueChangeTimeout = valueChangeTimeout;
        applyChangeTimeout();
    }

    @Override
    public int getValueChangeTimeout() {
        return valueChangeTimeout;
    }

    private void applyChangeTimeout() {
        ValueChangeMode.applyChangeTimeout(getValueChangeMode(),
                getValueChangeTimeout(), getSynchronizationRegistration());
    }

    @Override
    public void setValue(TValue value) {
        super.setValue(value);
        schedulePropertyConsistencyCheck();
    }

    private void schedulePropertyConsistencyCheck() {
        if (consistencyCheckPending) {
            return;
        }

        consistencyCheckPending = true;
        getElement().getNode().runWhenAttached(
                ui -> ui.beforeClientResponse(this, context -> {
                    consistencyCheckPending = false;
                    warnIfPropertiesInconsistent();
                }));
    }

    private void warnIfPropertiesInconsistent() {
        double min = getMinDouble();
        double max = getMaxDouble();
        TValue value = getValue();

        if (min > max) {
            LoggerFactory.getLogger(getClass()).warn(
                    "Invalid configuration: min ({}) is greater than max ({}).",
                    min, max);
        }

        if (min <= max && !isValueWithinMinMax(value)) {
            LoggerFactory.getLogger(getClass()).warn(
                    "Invalid configuration: value ({}) is outside the configured range (min={}, max={}).",
                    value, min, max);
        }

        if (min <= max && !isValueAlignedWithStep(value)) {
            LoggerFactory.getLogger(getClass()).warn(
                    "Invalid configuration: value ({}) is not aligned with step (min={}, max={}, step={}).",
                    value, min, max, getStepDouble());
        }
    }

    abstract protected boolean isValueAlignedWithStep(TValue value);

    abstract protected boolean isValueWithinMinMax(TValue value);
}
