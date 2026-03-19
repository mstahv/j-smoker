package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;
import in.virit.color.NamedColor;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.button.VButton;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;

/**
 * Simulation view for testing the automation without real smokerHardware.
 * Provides manual temperature injection and shows PID/state machine response in real time.
 */
@Route
@MenuItem(icon = VaadinIcon.FLASK, order = MenuItem.END)
public class SimulationView extends AbstractDiagramView {

    private final SmokerController controller;

    // Temperature injection controls
    private final NumberField chamberTempField = new NumberField("Chamber temperature (°C)");
    private final NumberField fireTempField = new NumberField("Fire box temperature (°C)");

    // Automation controls
    private final NumberField setpointField = new NumberField("Target temperature (°C)");
    private final Button startStopButton = new Button();
    private final Select<SmokerController.State> stateSelect = new Select<>();
    private final Select<SmokerController.ChamberSource> chamberSourceSelect = new Select<>();

    // Status display
    private final StatBadge stateLabel = new StatBadge("State");
    private final StatBadge chamberSourceLabel = new StatBadge("Active source");
    private final StatBadge throttleLabel = new StatBadge("Throttle", "%d%%");
    private final StatBadge blowerLabel = new StatBadge("Blower", "%d%%");
    private final StatBadge pidOutputLabel = new StatBadge("PID output", "%.1f / 200");
    private final StatBadge errorLabel = new StatBadge("Error", "%+.1f°C");
    private final StatBadge pLabel = new StatBadge("P", "%.1f");
    private final StatBadge iLabel = new StatBadge("I", "%.1f");
    private final StatBadge dLabel = new StatBadge("D", "%.1f");
    private final StatBadge fireRateLabel = new StatBadge("Fire Δ", "%+.1f°C/30s");
    private final StatBadge chamberRateLabel = new StatBadge("Chamber Δ", "%+.1f°C/30s");

    // Simulation active indicator
    private final Button simToggle = new Button();
    private final NumberField simSpeedField = new NumberField("Time acceleration");

    public SimulationView(SmokerHardware hardware, SmokerController controller, UiRefresher uiRefresher) {
        super(hardware, uiRefresher);
        this.controller = controller;

        add(new DiagramViewInfo("Use this view to test/play with the automation logic."));

        // Simulation toggle
        updateSimToggle();
        simToggle.addClickListener(e -> {
            smokerHardware.setSimulationMode(!smokerHardware.isSimulationMode());
            updateSimToggle();
        });

        // Time acceleration
        simSpeedField.setMin(1);
        simSpeedField.setMax(20);
        simSpeedField.setStep(1);
        simSpeedField.setValue((double) controller.getSimulationSpeed());
        simSpeedField.setStepButtonsVisible(true);
        simSpeedField.setSuffixComponent(new Span("x"));
        simSpeedField.setWidth("8em");
        simSpeedField.addValueChangeListener(e -> {
            if (e.getValue() != null) controller.setSimulationSpeed(e.getValue().intValue());
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
        fireTempField.setStep(1);
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
                if (!smokerHardware.isSimulationMode()) {
                    smokerHardware.setSimulationMode(true);
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
                if (!smokerHardware.isSimulationMode()) {
                    smokerHardware.setSimulationMode(true);
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
        var flameButton = new VButton("Simulate flame", e -> simulateFlame()){{
            getStyle().setBackgroundColor(NamedColor.LIGHTPINK);
        }};

        var woodButton = new VButton("Simulate wood addition", e -> simulateWoodAddition()){{
            getStyle().setBackgroundColor(NamedColor.LEMONCHIFFON);
        }};
        var lowFuelButton = new VButton("Simulate low fuel", e -> simulateLowFuel()){{
            getStyle().setBackgroundColor(NamedColor.NAVAJOWHITE);
        }};

        add(
                simToggle, simSpeedField,
                new H4("Temperature input"),
                new HorizontalFloatLayout(chamberTempField, fireTempField),
                new Hr(),
                new H4("Automatic control"),
                new HorizontalFloatLayout(setpointField, startStopButton, stateSelect, chamberSourceSelect),
                new Hr(),
                new H4("Scenarios"),
                new HorizontalFloatLayout(flameButton, woodButton, lowFuelButton),
                new Hr(),
                new ParameterPanel(controller),
                new Hr(),
                new H4("Status & diagnostics"),
                new StatGrid(
                        stateLabel, chamberSourceLabel,
                        throttleLabel, blowerLabel,
                        pidOutputLabel, errorLabel,
                        pLabel, iLabel,
                        dLabel, fireRateLabel,
                        chamberRateLabel
                )
        );

        updateStatus();
    }

    private void updateSimToggle() {
        boolean active = smokerHardware.isSimulationMode();
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
            smokerHardware.simulateReading(SmokerHardware.IBBQ_1, chamberTempField.getValue());
        }
        if (fireTempField.getValue() != null) {
            smokerHardware.simulateReading(SmokerHardware.PROBE, fireTempField.getValue());
        }
    }

    private void simulateFlame() {
        // Rapidly increase fire temperature to trigger flame detection
        double current = fireTempField.getValue() != null ? fireTempField.getValue() : 200;
        for (int i = 1; i <= 8; i++) {
            smokerHardware.simulateReading(SmokerHardware.PROBE, current + i * 5);
        }
        fireTempField.setValue(current + 40);
        Notification.show("Flame simulated — fire box temperature raised rapidly", 3000, Notification.Position.BOTTOM_START);
    }

    private void simulateWoodAddition() {
        // Rapidly drop fire temperature
        double current = fireTempField.getValue() != null ? fireTempField.getValue() : 200;
        for (int i = 1; i <= 8; i++) {
            smokerHardware.simulateReading(SmokerHardware.PROBE, current - i * 5);
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
        updateDiagram();
        updateStartStopButton();
        var state = controller.getState();
        stateSelect.setValue(state);

        stateLabel.setValue(AutomaticView.stateLabel(state));
        stateLabel.getStyle().setColor(AutomaticView.stateColor(state)).setFontWeight("bold");

        chamberSourceLabel.setValue(controller.getActiveChamberSourceKey());
        throttleLabel.setValue(controller.getLastThrottlePercent());
        blowerLabel.setValue(controller.getLastBlowerPercent());
        pidOutputLabel.setValue(controller.getLastPidOutput());
        errorLabel.setValue(controller.getLastError());
        pLabel.setValue(controller.getLastPTerm());
        iLabel.setValue(controller.getLastITerm());
        dLabel.setValue(controller.getLastDTerm());
        fireRateLabel.setValue(controller.getLastFireRate());
        chamberRateLabel.setValue(controller.getLastChamberRate());

        // Drain and show alerts
        for (String alert : controller.drainAlerts()) {
            Notification notification = Notification.show(alert, 10_000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
        }

        // Keep injecting current temperatures in simulation mode
        if (smokerHardware.isSimulationMode() && controller.getState() != SmokerController.State.OFF) {
            injectTemperatures();
        }
    }

    @Override
    protected void onRefresh() {
        updateStatus();
    }
}
