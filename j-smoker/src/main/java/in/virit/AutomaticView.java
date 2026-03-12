package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.firitin.util.style.LumoProps;

@Route
@MenuItem(icon = VaadinIcon.MAGIC)
public class AutomaticView extends VVerticalLayout {

    private final SmokerController controller;
    private final UiRefresher uiRefresher;

    private final NumberField setpointField = new NumberField("Target temperature (°C)");
    private final Button startStopButton = new Button();
    private final Select<SmokerController.State> stateSelect = new Select<>();
    private final Select<SmokerController.ChamberSource> chamberSourceSelect = new Select<>();
    private final StateIndicator stateIndicator = new StateIndicator();
    private final ActuatorStatus actuatorStatus = new ActuatorStatus();
    private final PidDiagnostics pidDiagnostics = new PidDiagnostics();

    public AutomaticView(SmokerController controller, UiRefresher uiRefresher) {
        this.controller = controller;
        this.uiRefresher = uiRefresher;

        setpointField.setMin(60);
        setpointField.setMax(180);
        setpointField.setStep(5);
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

        var controls = new HorizontalLayout(setpointField, startStopButton, stateSelect, chamberSourceSelect) {{
            setAlignItems(Alignment.BASELINE);
            getStyle().set("flex-wrap", "wrap");
        }};

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
        setpointField.setEnabled(!running);
    }

    private void updateView() {
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
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), this::updateView);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
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

    /**
     * State indicator badge with color coding.
     */
    static class StateIndicator extends Div {

        private final Span badge = new Span();

        StateIndicator() {
            add(new H4("State"), badge);
            badge.getStyle()
                    .setPadding(LumoProps.SPACE_XS.var() + " " + LumoProps.SPACE_M.var())
                    .setBorderRadius(LumoProps.BORDER_RADIUS_M.var())
                    .setFontWeight("bold");
        }

        void update(SmokerController.State state) {
            String textColor = "white";
            String bgColor = switch (state) {
                case OFF -> "gray";
                case HEATING -> "#e65100";
                case SMOKING -> "#2e7d32";
                case FLAME_ALERT -> "#c62828";
                case LOW_FUEL -> { textColor = "black"; yield "#f9a825"; }
            };
            badge.setText(stateLabel(state));
            badge.getStyle()
                    .setBackground(bgColor)
                    .setColor(textColor);
        }
    }

    /**
     * Shows current throttle and blower percentages plus temperatures.
     */
    static class ActuatorStatus extends Div {

        private final Span throttleLabel = new Span();
        private final Span blowerLabel = new Span();
        private final Span chamberLabel = new Span();
        private final Span fireLabel = new Span();
        private final Span sourceLabel = new Span();

        ActuatorStatus() {
            add(new H4("Actuators & temperatures"));

            var grid = new Div(throttleLabel, blowerLabel, chamberLabel, fireLabel, sourceLabel);
            grid.getStyle()
                    .setDisplay(com.vaadin.flow.dom.Style.Display.GRID)
                    .set("grid-template-columns", "1fr 1fr")
                    .set("gap", LumoProps.SPACE_S.var());
            add(grid);

            for (var label : new Span[]{throttleLabel, blowerLabel, chamberLabel, fireLabel, sourceLabel}) {
                label.getStyle()
                        .setPadding(LumoProps.SPACE_XS.var() + " " + LumoProps.SPACE_S.var())
                        .setBackground(LumoProps.CONTRAST_5PCT.var())
                        .setBorderRadius(LumoProps.BORDER_RADIUS_S.var());
            }
        }

        void update(int throttle, int blower, double chamberTemp, double fireTemp, String chamberSource) {
            throttleLabel.setText("Throttle: %d%%".formatted(throttle));
            blowerLabel.setText("Blower: %d%%".formatted(blower));
            chamberLabel.setText("Chamber: %s".formatted(
                    Double.isNaN(chamberTemp) ? "–" : "%.1f°C".formatted(chamberTemp)));
            fireLabel.setText("Fire box: %s".formatted(
                    Double.isNaN(fireTemp) ? "–" : "%.1f°C".formatted(fireTemp)));
            sourceLabel.setText("Source: %s".formatted(chamberSource));
        }
    }

    /**
     * PID diagnostics panel for tuning.
     */
    static class PidDiagnostics extends Div {

        private final Span errorLabel = new Span();
        private final Span pLabel = new Span();
        private final Span iLabel = new Span();
        private final Span dLabel = new Span();
        private final Span outputLabel = new Span();
        private final Span fireRateLabel = new Span();
        private final Span chamberRateLabel = new Span();

        PidDiagnostics() {
            add(new H4("PID diagnostics"));

            var grid = new Div(errorLabel, outputLabel, pLabel, iLabel, dLabel, fireRateLabel, chamberRateLabel);
            grid.getStyle()
                    .setDisplay(com.vaadin.flow.dom.Style.Display.GRID)
                    .set("grid-template-columns", "1fr 1fr")
                    .set("gap", LumoProps.SPACE_XS.var())
                    .setFontSize(LumoProps.FONT_SIZE_S.var());
            add(grid);

            for (var label : new Span[]{errorLabel, pLabel, iLabel, dLabel, outputLabel, fireRateLabel, chamberRateLabel}) {
                label.getStyle()
                        .setPadding(LumoProps.SPACE_XS.var() + " " + LumoProps.SPACE_S.var())
                        .setBackground(LumoProps.CONTRAST_5PCT.var())
                        .setBorderRadius(LumoProps.BORDER_RADIUS_S.var())
                        .set("font-family", "monospace");
            }
        }

        void update(double error, double p, double i, double d, double output, double fireRate, double chamberRate) {
            errorLabel.setText("Error: %+.1f°C".formatted(error));
            pLabel.setText("P: %.1f".formatted(p));
            iLabel.setText("I: %.1f".formatted(i));
            dLabel.setText("D: %.1f".formatted(d));
            outputLabel.setText("PID output: %.1f / 200".formatted(output));
            fireRateLabel.setText("Fire Δ: %+.1f°C/30s".formatted(fireRate));
            chamberRateLabel.setText("Chamber Δ: %+.1f°C/30s".formatted(chamberRate));
        }
    }
}
