package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.firitin.util.style.AuraProps;
import org.vaadin.firitin.util.style.VaadinCssProps;

/**
 * Simulation view for testing the automation without real hardware.
 * Provides manual temperature injection and shows PID/state machine response in real time.
 */
@Route
@MenuItem(icon = VaadinIcon.FLASK)
public class SimulationView extends VVerticalLayout {

    private final SmokerHardware hardware;
    private final SmokerController controller;
    private final UiRefresher uiRefresher;

    // Temperature injection controls
    private final NumberField chamberTempField = new NumberField("Chamber temperature (°C)");
    private final NumberField fireTempField = new NumberField("Fire box temperature (°C)");

    // Automation controls
    private final NumberField setpointField = new NumberField("Target temperature (°C)");
    private final Button startStopButton = new Button();
    private final Select<SmokerController.State> stateSelect = new Select<>();
    private final Select<SmokerController.ChamberSource> chamberSourceSelect = new Select<>();

    // Status display
    private final Span stateLabel = new Span();
    private final Span chamberSourceLabel = new Span();
    private final Span throttleLabel = new Span();
    private final Span blowerLabel = new Span();
    private final Span pidOutputLabel = new Span();
    private final Span errorLabel = new Span();
    private final Span pLabel = new Span();
    private final Span iLabel = new Span();
    private final Span dLabel = new Span();
    private final Span fireRateLabel = new Span();
    private final Span chamberRateLabel = new Span();

    // Simulation active indicator
    private final Button simToggle = new Button();

    public SimulationView(SmokerHardware hardware, SmokerController controller, UiRefresher uiRefresher) {
        this.hardware = hardware;
        this.controller = controller;
        this.uiRefresher = uiRefresher;

        // Simulation toggle
        updateSimToggle();
        simToggle.addClickListener(e -> {
            hardware.setSimulationMode(!hardware.isSimulationMode());
            updateSimToggle();
        });

        // Temperature injection
        chamberTempField.setMin(0);
        chamberTempField.setMax(400);
        chamberTempField.setStep(1);
        chamberTempField.setValue(80.0);
        chamberTempField.setStepButtonsVisible(true);
        chamberTempField.setSuffixComponent(new Span("°C"));
        chamberTempField.addValueChangeListener(e -> injectTemperatures());

        fireTempField.setMin(0);
        fireTempField.setMax(600);
        fireTempField.setStep(5);
        fireTempField.setValue(200.0);
        fireTempField.setStepButtonsVisible(true);
        fireTempField.setSuffixComponent(new Span("°C"));
        fireTempField.addValueChangeListener(e -> injectTemperatures());

        // Automation controls
        setpointField.setMin(60);
        setpointField.setMax(180);
        setpointField.setStep(5);
        setpointField.setValue(controller.getSetpoint());
        setpointField.setStepButtonsVisible(true);
        setpointField.setSuffixComponent(new Span("°C"));

        updateStartStopButton();
        startStopButton.addClickListener(e -> {
            if (controller.getState() == SmokerController.State.OFF) {
                if (!hardware.isSimulationMode()) {
                    hardware.setSimulationMode(true);
                    updateSimToggle();
                }
                // Inject initial temperatures before starting
                injectTemperatures();
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
                if (!hardware.isSimulationMode()) {
                    hardware.setSimulationMode(true);
                    updateSimToggle();
                }
                injectTemperatures();
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

        // Scenario buttons
        var flameButton = new Button("Simulate flame", e -> simulateFlame());
        flameButton.getStyle().setBackground("#ffcdd2");

        var woodButton = new Button("Simulate wood addition", e -> simulateWoodAddition());
        woodButton.getStyle().setBackground("#fff9c4");

        var lowFuelButton = new Button("Simulate low fuel", e -> simulateLowFuel());
        lowFuelButton.getStyle().setBackground("#ffe0b2");

        var scenarioBar = new Div(flameButton, woodButton, lowFuelButton) {{
            getStyle()
                    .setDisplay(com.vaadin.flow.dom.Style.Display.FLEX)
                    .set("gap", VaadinCssProps.GAP_S.var())
                    .set("flex-wrap", "wrap");
        }};

        // Status grid
        var statusGrid = new Div(
                stateLabel, chamberSourceLabel,
                throttleLabel, blowerLabel,
                pidOutputLabel, errorLabel,
                pLabel, iLabel,
                dLabel, fireRateLabel,
                chamberRateLabel
        ) {{
            getStyle()
                    .setDisplay(com.vaadin.flow.dom.Style.Display.GRID)
                    .set("grid-template-columns", "1fr 1fr")
                    .set("gap", VaadinCssProps.GAP_XS.var());
        }};

        for (var label : new Span[]{stateLabel, chamberSourceLabel, throttleLabel, blowerLabel,
                pidOutputLabel, errorLabel, pLabel, iLabel, dLabel, fireRateLabel, chamberRateLabel}) {
            label.getStyle()
                    .setPadding(VaadinCssProps.PADDING_XS.var() + " " + VaadinCssProps.PADDING_S.var())
                    .setBackground(AuraProps.SURFACE_COLOR.var())
                    .setBorderRadius(VaadinCssProps.RADIUS_S.var())
                    .set("font-family", "monospace")
                    .setFontSize(AuraProps.FONT_SIZE_S.var());
        }

        add(
                simToggle,
                new H4("Temperature input"),
                new Div(chamberTempField, fireTempField) {{
                    getStyle()
                            .setDisplay(com.vaadin.flow.dom.Style.Display.FLEX)
                            .set("gap", VaadinCssProps.GAP_M.var())
                            .set("flex-wrap", "wrap");
                }},
                new Hr(),
                new H4("Automatic control"),
                new Div(setpointField, startStopButton, stateSelect, chamberSourceSelect) {{
                    getStyle()
                            .setDisplay(com.vaadin.flow.dom.Style.Display.FLEX)
                            .set("gap", VaadinCssProps.GAP_M.var())
                            .set("flex-wrap", "wrap")
                            .set("align-items", "baseline");
                }},
                new Hr(),
                new H4("Scenarios"),
                scenarioBar,
                new Hr(),
                new ParameterPanel(controller),
                new Hr(),
                new H4("Status & diagnostics"),
                statusGrid
        );

        updateStatus();
    }

    private void updateSimToggle() {
        boolean active = hardware.isSimulationMode();
        simToggle.setText(active ? "Simulation ON — click to disable" : "Simulation OFF — click to enable");
        if (active) {
            simToggle.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            simToggle.removeThemeVariants(ButtonVariant.LUMO_TERTIARY);
        } else {
            simToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            simToggle.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
        }
    }

    private void injectTemperatures() {
        if (chamberTempField.getValue() != null) {
            hardware.simulateReading(SmokerHardware.IBBQ_1, chamberTempField.getValue());
        }
        if (fireTempField.getValue() != null) {
            hardware.simulateReading(SmokerHardware.PROBE, fireTempField.getValue());
        }
    }

    private void simulateFlame() {
        // Rapidly increase fire temperature to trigger flame detection
        double current = fireTempField.getValue() != null ? fireTempField.getValue() : 200;
        for (int i = 1; i <= 8; i++) {
            hardware.simulateReading(SmokerHardware.PROBE, current + i * 5);
        }
        fireTempField.setValue(current + 40);
        Notification.show("Flame simulated — fire box temperature raised rapidly", 3000, Notification.Position.BOTTOM_START);
    }

    private void simulateWoodAddition() {
        // Rapidly drop fire temperature
        double current = fireTempField.getValue() != null ? fireTempField.getValue() : 200;
        for (int i = 1; i <= 8; i++) {
            hardware.simulateReading(SmokerHardware.PROBE, current - i * 5);
        }
        fireTempField.setValue(current - 40);
        Notification.show("Wood addition simulated — fire box temperature dropped", 3000, Notification.Position.BOTTOM_START);
    }

    private void simulateLowFuel() {
        // Slowly decrease fire temperature (simulate over many ticks)
        double current = fireTempField.getValue() != null ? fireTempField.getValue() : 200;
        fireTempField.setValue(current - 30);
        // Also set chamber low to force high PID output
        chamberTempField.setValue(controller.getSetpoint() - 20);
        injectTemperatures();
        Notification.show("Low fuel simulated — fire box cooling, chamber dropping", 3000, Notification.Position.BOTTOM_START);
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

    private void updateStatus() {
        updateStartStopButton();
        var state = controller.getState();
        stateSelect.setValue(state);

        String stateColor = switch (state) {
            case OFF -> "gray";
            case HEATING -> "#e65100";
            case SMOKING -> "#2e7d32";
            case FLAME_ALERT -> "#c62828";
            case LOW_FUEL -> "#f9a825";
        };
        stateLabel.setText("State: %s".formatted(AutomaticView.stateLabel(state)));
        stateLabel.getStyle().setColor(stateColor).setFontWeight("bold");

        chamberSourceLabel.setText("Active source: %s".formatted(controller.getActiveChamberSourceKey()));
        throttleLabel.setText("Throttle: %d%%".formatted(controller.getLastThrottlePercent()));
        blowerLabel.setText("Blower: %d%%".formatted(controller.getLastBlowerPercent()));
        pidOutputLabel.setText("PID output: %.1f / 200".formatted(controller.getLastPidOutput()));
        errorLabel.setText("Error: %+.1f°C".formatted(controller.getLastError()));
        pLabel.setText("P: %.1f".formatted(controller.getLastPTerm()));
        iLabel.setText("I: %.1f".formatted(controller.getLastITerm()));
        dLabel.setText("D: %.1f".formatted(controller.getLastDTerm()));
        fireRateLabel.setText("Fire Δ: %+.1f°C/30s".formatted(controller.getLastFireRate()));
        chamberRateLabel.setText("Chamber Δ: %+.1f°C/30s".formatted(controller.getLastChamberRate()));

        // Drain and show alerts
        for (String alert : controller.drainAlerts()) {
            Notification notification = Notification.show(alert, 10_000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
        }

        // Keep injecting current temperatures in simulation mode
        if (hardware.isSimulationMode() && controller.getState() != SmokerController.State.OFF) {
            injectTemperatures();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), this::updateStatus);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }
}
