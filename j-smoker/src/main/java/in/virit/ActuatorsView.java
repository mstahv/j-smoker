package in.virit;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.github.mstahv.sliders.IntSlider;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.checkbox.ToggleButton;
import org.vaadin.firitin.util.style.LumoProps;

@Route
@MenuItem(icon = VaadinIcon.ADJUST)
public class ActuatorsView extends VerticalLayout {

    public ActuatorsView(SmokerHardware smokerHardware) {
        var throttle = new IntSlider("Throttle", 0, 100, 0) {{
            setMinLabel("0%");
            setMaxLabel("100%");
            addValueChangeListener(e -> smokerHardware.setThrottle(e.getValue()));
        }};

        var blower = new ToggleButton("Supercharger (blower)") {{
            addValueChangeListener(e -> smokerHardware.setBlower(e.getValue()));
        }};

        add(throttle, blower);

        setSpacing(LumoProps.SPACE_XL.var());
    }
}
