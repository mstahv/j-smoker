package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.router.Route;
import in.virit.slider.NumberSlider;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.util.style.VaadinCssProps;

@Route
@MenuItem(icon = VaadinIcon.ADJUST)
public class ActuatorsView extends VerticalLayout {

    private static final String BLOWER_OFF = "Off";
    private static final String BLOWER_ON = "Full on";
    private static final String BLOWER_PWM = "Software PWM";

    private final SmokerHardware smokerHardware;
    private final UiRefresher uiRefresher;

    private final WarningMessage autoWarning = new WarningMessage(
            "Automatic control active — manual controls disabled");
    private final AirflowDiagram diagram = new AirflowDiagram();
    private final NumberSlider<Integer> throttle;
    private final RadioButtonGroup<String> blowerMode;
    private final NumberSlider<Integer> blowerDuty;

    public ActuatorsView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;

        autoWarning.setVisible(false);

        throttle = new NumberSlider<>(Integer.class) {{
            setLabel("Throttle");
            setWidthFull();
            setMinMaxVisible(true);
            addValueChangeListener(e -> {
                if (!smokerHardware.isAutomaticControlActive()) {
                    smokerHardware.setThrottle(e.getValue());
                    setLabel("Throttle %s %%".formatted(e.getValue()));
                }
                diagram.setThrottlePercent(e.getValue());
            });
        }};

        blowerMode = new RadioButtonGroup<>("Supercharger (blower)");
        blowerMode.setItems(BLOWER_OFF, BLOWER_ON, BLOWER_PWM);
        blowerMode.setValue(BLOWER_OFF);

        blowerDuty = new NumberSlider<>(Integer.class) {{
            setLabel("Blower");
            setValue(50);
            setWidthFull();
            setMinMaxVisible(true);
            setVisible(false);
            addValueChangeListener(e -> {
                if (!smokerHardware.isAutomaticControlActive()
                        && blowerMode.getValue().equals(BLOWER_PWM)) {
                    if (e.getValue() <= 0) {
                        smokerHardware.disableBlower();
                    } else {
                        smokerHardware.setBlowerDuty(e.getValue());
                    }
                    setLabel("Blower %s %%".formatted(e.getValue()));
                }
                diagram.setBlowerSpeed(e.getValue());
                diagram.setBlowerLabel("Blower PWM %d %%".formatted(e.getValue()));
            });
        }};

        blowerMode.addValueChangeListener(e -> {
            if (smokerHardware.isAutomaticControlActive()) return;
            switch (e.getValue()) {
                case BLOWER_OFF -> {
                    smokerHardware.disableBlower();
                    blowerDuty.setVisible(false);
                    diagram.setBlowerSpeed(0);
                    diagram.setBlowerLabel("Blower OFF");
                }
                case BLOWER_ON -> {
                    smokerHardware.setBlower(true);
                    blowerDuty.setVisible(false);
                    diagram.setBlowerSpeed(100);
                    diagram.setBlowerLabel("Blower FULL");
                }
                case BLOWER_PWM -> {
                    smokerHardware.setBlowerDuty(blowerDuty.getValue());
                    blowerDuty.setVisible(true);
                    diagram.setBlowerSpeed(blowerDuty.getValue());
                    diagram.setBlowerLabel("Blower PWM %d %%".formatted(blowerDuty.getValue()));
                }
            }
        });

        diagram.onThrottleDrag(percent -> {
            if (!smokerHardware.isAutomaticControlActive()) {
                throttle.setValue(percent);
            }
        });

        diagram.onBlowerClick(mode -> {
            if (smokerHardware.isAutomaticControlActive()) return;
            switch (mode) {
                case OFF -> blowerMode.setValue(BLOWER_OFF);
                case PWM_50 -> {
                    blowerMode.setValue(BLOWER_PWM);
                    blowerDuty.setValue(50);
                }
                case FULL -> blowerMode.setValue(BLOWER_ON);
            }
        });

        add(autoWarning, diagram, throttle, blowerMode, blowerDuty);
        setSpacing(VaadinCssProps.GAP_XL.var());

        // Initialize components with current hardware state
        int currentThrottle = smokerHardware.getThrottlePercent();
        throttle.setValue(currentThrottle);
        diagram.setThrottlePercent(currentThrottle);

        if (smokerHardware.isBlowerForceOn()) {
            blowerMode.setValue(BLOWER_ON);
        } else if (smokerHardware.isBlowerSoftPwmEnabled()) {
            blowerMode.setValue(BLOWER_PWM);
            blowerDuty.setValue(smokerHardware.getBlowerDutyPercent());
            blowerDuty.setLabel("Blower %s %%".formatted(smokerHardware.getBlowerDutyPercent()));
            blowerDuty.setVisible(true);
        }
        // blowerMode value change listener already syncs the diagram

        updateAutoState();
    }

    private void updateAutoState() {
        boolean autoActive = smokerHardware.isAutomaticControlActive();
        autoWarning.setVisible(autoActive);
        throttle.setEnabled(!autoActive);
        blowerMode.setEnabled(!autoActive);
        blowerDuty.setEnabled(!autoActive);
        if (autoActive) {
            // Sync diagram with hardware state changed by auto-control
            diagram.setThrottlePercent(smokerHardware.getThrottlePercent());
            int blower = smokerHardware.getBlowerPercent();
            diagram.setBlowerSpeed(blower);
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), this::updateAutoState);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }
}
