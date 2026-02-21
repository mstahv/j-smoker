package in.virit;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.router.Route;
import io.github.mstahv.sliders.IntSlider;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.checkbox.ToggleButton;
import org.vaadin.firitin.util.style.LumoProps;

@Route
@MenuItem(icon = VaadinIcon.ADJUST)
public class ActuatorsView extends VerticalLayout {

    private static final String BLOWER_OFF = "Off";
    private static final String BLOWER_ON = "Full on";
    private static final String BLOWER_PWM = "Software PWM";

    public ActuatorsView(SmokerHardware smokerHardware) {
        var throttle = new IntSlider("Throttle", 0, 100, 0) {{
            setMinLabel("0%");
            setMaxLabel("100%");
            addValueChangeListener(e -> smokerHardware.setThrottle(e.getValue()));
        }};

        var blowerMode = new RadioButtonGroup<String>("Supercharger (blower)");
        blowerMode.setItems(BLOWER_OFF, BLOWER_ON, BLOWER_PWM);
        blowerMode.setValue(BLOWER_OFF);

        var blowerDuty = new IntSlider("Blower power", 1, 100, 50) {{
            setMinLabel("1%");
            setMaxLabel("100%");
            setVisible(false);
            addValueChangeListener(e -> {
                if (blowerMode.getValue().equals(BLOWER_PWM)) {
                    smokerHardware.setBlowerDuty(e.getValue());
                }
            });
        }};

        blowerMode.addValueChangeListener(e -> {
            switch (e.getValue()) {
                case BLOWER_OFF -> {
                    smokerHardware.disableBlower();
                    blowerDuty.setVisible(false);
                }
                case BLOWER_ON -> {
                    smokerHardware.setBlower(true);
                    blowerDuty.setVisible(false);
                }
                case BLOWER_PWM -> {
                    smokerHardware.setBlowerDuty(blowerDuty.getValue());
                    blowerDuty.setVisible(true);
                }
            }
        });

        add(throttle, blowerMode, blowerDuty);

        setSpacing(LumoProps.SPACE_XL.var());
    }
}
