package in.virit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.NumberField;
import org.vaadin.firitin.util.style.VaadinCssProps;

/**
 * Shared panel for tuning PID and safety parameters.
 * Used in both AutomaticView and SimulationView.
 */
class ParameterPanel extends Div {

    private final SmokerController controller;

    private final NumberField kpField = pidField("Kp", 0, 20, 0.5);
    private final NumberField kiField = pidField("Ki", 0, 1, 0.005);
    private final NumberField kdField = pidField("Kd", 0, 10, 0.1);

    private final NumberField flameThresholdField = safetyField("Flame threshold (°C/30s)", 1, 50, 1);
    private final NumberField flameRecoveryField = safetyField("Flame recovery (°C/30s)", 0, 30, 1);
    private final NumberField woodDropField = safetyField("Wood addition (°C/30s)", -100, 0, 5);
    private final NumberField lowFuelField = safetyField("Low fuel threshold (PID output)", 50, 200, 10);

    ParameterPanel(SmokerController controller) {
        this.controller = controller;

        var pid = controller.getPid();
        kpField.setValue(pid.getKp());
        kiField.setValue(pid.getKi());
        kdField.setValue(pid.getKd());

        kpField.addValueChangeListener(e -> { if (e.getValue() != null) pid.setKp(e.getValue()); });
        kiField.addValueChangeListener(e -> { if (e.getValue() != null) pid.setKi(e.getValue()); });
        kdField.addValueChangeListener(e -> { if (e.getValue() != null) pid.setKd(e.getValue()); });

        flameThresholdField.setValue(controller.getFlameRateThreshold());
        flameRecoveryField.setValue(controller.getFlameRecoveryThreshold());
        woodDropField.setValue(controller.getWoodAdditionDropThreshold());
        lowFuelField.setValue(controller.getLowFuelOutputThreshold());

        flameThresholdField.addValueChangeListener(e -> {
            if (e.getValue() != null) controller.setFlameRateThreshold(e.getValue());
        });
        flameRecoveryField.addValueChangeListener(e -> {
            if (e.getValue() != null) controller.setFlameRecoveryThreshold(e.getValue());
        });
        woodDropField.addValueChangeListener(e -> {
            if (e.getValue() != null) controller.setWoodAdditionDropThreshold(e.getValue());
        });
        lowFuelField.addValueChangeListener(e -> {
            if (e.getValue() != null) controller.setLowFuelOutputThreshold(e.getValue());
        });

        var pidGrid = new Div(kpField, kiField, kdField) {{
            getStyle()
                    .setDisplay(com.vaadin.flow.dom.Style.Display.FLEX)
                    .set("gap", VaadinCssProps.GAP_M.var())
                    .set("flex-wrap", "wrap");
        }};

        var safetyGrid = new Div(flameThresholdField, flameRecoveryField, woodDropField, lowFuelField) {{
            getStyle()
                    .setDisplay(com.vaadin.flow.dom.Style.Display.FLEX)
                    .set("gap", VaadinCssProps.GAP_M.var())
                    .set("flex-wrap", "wrap");
        }};

        add(new H4("PID parameters"), pidGrid, new Hr(), new H4("Safety thresholds"), safetyGrid);
    }

    private static NumberField pidField(String label, double min, double max, double step) {
        var field = new NumberField(label);
        field.setMin(min);
        field.setMax(max);
        field.setStep(step);
        field.setStepButtonsVisible(true);
        field.setWidth("8em");
        return field;
    }

    private static NumberField safetyField(String label, double min, double max, double step) {
        var field = new NumberField(label);
        field.setMin(min);
        field.setMax(max);
        field.setStep(step);
        field.setStepButtonsVisible(true);
        field.setWidth("10em");
        return field;
    }
}
