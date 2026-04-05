package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;
import in.virit.color.Color;
import in.virit.color.NamedColor;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.style.VaadinCssProps;

@Route
@MenuItem(icon = VaadinIcon.MAGIC)
public class AutomaticView extends AbstractDiagramView {

    private final SmokerController controller;

    private final NumberField setpointField = new NumberField("Target temperature (°C)");
    private final Button startStopButton = new Button();
    private final Select<SmokerController.State> stateSelect = new Select<>();
    private final Select<SmokerController.ChamberSource> chamberSourceSelect = new Select<>();
    private final StateIndicator stateIndicator = new StateIndicator();
    private final ActuatorStatus actuatorStatus = new ActuatorStatus();
    private final PidDiagnostics pidDiagnostics = new PidDiagnostics();

    public AutomaticView(SmokerController controller, SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        super(smokerHardware, uiRefresher);
        this.controller = controller;

        add(new DiagramViewInfo("Set or configure automation, the does best effort to maintain target temperature" +
                "by controlling the airflow."));

        setpointField.setMin(60);
        setpointField.setMax(180);
        setpointField.setStep(1);
        setpointField.setValue(controller.getSetpoint());
        setpointField.setSuffixComponent(new Span("°C"));
        setpointField.setStepButtonsVisible(true);
        setpointField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                controller.setSetpoint(e.getValue());
            }
        });

        updateStartStopButton();
        startStopButton.addClickListener(e -> {
            if (controller.getState() == SmokerController.State.OFF) {
                controller.start(setpointField.getValue());
            } else {
                controller.stop();
            }
            updateStartStopButton();
        });

        stateSelect.setLabel("Force state");
        stateSelect.setItems(SmokerController.State.values());
        stateSelect.setItemLabelGenerator(AutomaticView::stateLabel);
        stateSelect.setValue(controller.getState());
        stateSelect.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                controller.forceState(e.getValue());
                updateStartStopButton();
            }
        });

        chamberSourceSelect.setLabel("Chamber source");
        chamberSourceSelect.setItems(SmokerController.ChamberSource.values());
        chamberSourceSelect.setValue(controller.getPreferredChamberSource());
        chamberSourceSelect.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                controller.setPreferredChamberSource(e.getValue());
            }
        });

        var controls = new HorizontalFloatLayout(setpointField, startStopButton, stateSelect, chamberSourceSelect);

        add(controls, stateIndicator, actuatorStatus, pidDiagnostics, new ParameterPanel(controller));
        updateView();
    }

    private void updateStartStopButton() {
        boolean running = controller.getState() != SmokerController.State.OFF;
        startStopButton.setText(running ? "Stop" : "Start");
        if (running) {
            startStopButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            startStopButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
        } else {
            startStopButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            startStopButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
        }
    }

    private void updateView() {
        updateDiagram();
        updateStartStopButton();
        var state = controller.getState();

        stateSelect.setValue(state);
        stateIndicator.update(state);

        actuatorStatus.update(
                controller.getLastThrottlePercent(),
                controller.getLastBlowerPercent(),
                controller.getLastChamberTemp(),
                controller.getLastFireTemp(),
                controller.getActiveChamberSourceKey()
        );

        pidDiagnostics.update(
                controller.getLastError(),
                controller.getLastPTerm(),
                controller.getLastITerm(),
                controller.getLastDTerm(),
                controller.getLastPidOutput(),
                controller.getLastFireRate(),
                controller.getLastChamberRate()
        );

        // Drain and show alerts
        for (String alert : controller.drainAlerts()) {
            Notification notification = Notification.show(alert, 10_000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    @Override
    protected void onRefresh(java.util.List<AppEvent> events) {
        updateView();
        for (var event : events) {
            if (event instanceof AppEvent.SetpointChanged sc) {
                setpointField.setValue(sc.setpoint());
            }
        }
    }

    static String stateLabel(SmokerController.State state) {
        return switch (state) {
            case OFF -> "Off";
            case HEATING -> "Heating";
            case SMOKING -> "Smoking";
            case FLAME_ALERT -> "Flame alert";
            case LOW_FUEL -> "Low fuel";
        };
    }

    static Color stateColor(SmokerController.State state) {
        return switch (state) {
            case OFF -> NamedColor.GRAY;
            case HEATING -> NamedColor.DARKORANGE;
            case SMOKING -> NamedColor.FORESTGREEN;
            case FLAME_ALERT -> NamedColor.FIREBRICK;
            case LOW_FUEL -> NamedColor.GOLDENROD;
        };
    }

    /**
     * State indicator badge with color coding.
     */
    static class StateIndicator extends Div {

        private final Span badge = new Span();

        StateIndicator() {
            add(new H4("State"), badge);
            badge.getStyle()
                    .setPadding(VaadinCssProps.PADDING_XS.var() + " " + VaadinCssProps.PADDING_M.var())
                    .setBorderRadius(VaadinCssProps.RADIUS_M.var())
                    .setFontWeight("bold");
        }

        void update(SmokerController.State state) {
            Color textColor = state == SmokerController.State.LOW_FUEL ? NamedColor.BLACK : NamedColor.WHITE;
            badge.setText(stateLabel(state));
            badge.getStyle()
                    .setBackground(stateColor(state).toString())
                    .setColor(textColor.toString());
        }
    }

    /**
     * Shows current throttle and blower percentages plus temperatures.
     */
    static class ActuatorStatus extends Div {

        private final StatBadge throttleLabel = new StatBadge("Throttle", "%d%%");
        private final StatBadge blowerLabel = new StatBadge("Blower", "%d%%");
        private final StatBadge chamberLabel = new StatBadge("Chamber", "%.1f°C");
        private final StatBadge fireLabel = new StatBadge("Fire box", "%.1f°C");
        private final StatBadge sourceLabel = new StatBadge("Source");

        ActuatorStatus() {
            add(new H4("Actuators & temperatures"));

            add(new StatGrid(throttleLabel, blowerLabel, chamberLabel, fireLabel, sourceLabel));
        }

        void update(int throttle, int blower, double chamberTemp, double fireTemp, String chamberSource) {
            throttleLabel.setValue(throttle);
            blowerLabel.setValue(blower);
            chamberLabel.setValue(Double.isNaN(chamberTemp) ? "–" : "%.1f°C".formatted(chamberTemp));
            fireLabel.setValue(Double.isNaN(fireTemp) ? "–" : "%.1f°C".formatted(fireTemp));
            sourceLabel.setValue(chamberSource);
        }
    }

    /**
     * PID diagnostics panel for tuning.
     */
    static class PidDiagnostics extends Div {

        private final StatBadge errorLabel = new StatBadge("Error", "%+.1f°C");
        private final StatBadge pLabel = new StatBadge("P", "%.1f");
        private final StatBadge iLabel = new StatBadge("I", "%.1f");
        private final StatBadge dLabel = new StatBadge("D", "%.1f");
        private final StatBadge outputLabel = new StatBadge("PID output", "%.1f / 200");
        private final StatBadge fireRateLabel = new StatBadge("Fire Δ", "%+.1f°C/30s");
        private final StatBadge chamberRateLabel = new StatBadge("Chamber Δ", "%+.1f°C/30s");

        PidDiagnostics() {
            add(new H4("PID diagnostics"));

            add(new StatGrid(errorLabel, outputLabel, pLabel, iLabel, dLabel, fireRateLabel, chamberRateLabel));
        }

        void update(double error, double p, double i, double d, double output, double fireRate, double chamberRate) {
            errorLabel.setValue(error);
            pLabel.setValue(p);
            iLabel.setValue(i);
            dLabel.setValue(d);
            outputLabel.setValue(output);
            fireRateLabel.setValue(fireRate);
            chamberRateLabel.setValue(chamberRate);
        }
    }
}
