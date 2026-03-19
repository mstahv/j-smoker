package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.dom.Style;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;

/**
 * Base class for views that display the {@link AirflowDiagram}.
 * Provides the diagram instance, common hardware-to-diagram sync logic,
 * and automatic UI refresh registration.
 */
public abstract class AbstractDiagramView extends VVerticalLayout {

    protected final AirflowDiagram diagram = new AirflowDiagram();
    protected final SmokerHardware smokerHardware;
    private final UiRefresher uiRefresher;

    protected AbstractDiagramView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;
        getStyle().setPosition(Style.Position.RELATIVE);
        add(diagram);
    }

    /**
     * Syncs the diagram with current hardware state (throttle, blower, temperatures).
     */
    protected void updateDiagram() {
        diagram.setThrottlePercent(smokerHardware.getThrottlePercent());
        diagram.setBlowerSpeed(smokerHardware.getBlowerPercent());
        if (smokerHardware.isBlowerForceOn()) {
            diagram.setBlowerLabel("Blower FULL");
        } else if (smokerHardware.isBlowerSoftPwmEnabled()) {
            diagram.setBlowerLabel("Blower PWM %d %%".formatted(smokerHardware.getBlowerDutyPercent()));
        } else {
            diagram.setBlowerLabel("Blower OFF");
        }
        var fire = smokerHardware.getLatestReading(SmokerHardware.PROBE);
        if (fire != null) {
            diagram.setFireTemp("%.0f °C".formatted(fire.temperature()));
        }
        var chamber = smokerHardware.getLatestReading(SmokerHardware.IBBQ_1);
        if (chamber != null) {
            diagram.setFoodChamberTemp("%.0f °C".formatted(chamber.temperature()));
        }
        var food = smokerHardware.getLatestReading(SmokerHardware.IBBQ_2);
        if (food != null) {
            diagram.setFoodProbeTemp("%.0f °C".formatted(food.temperature()));
        }
    }

    /**
     * Called periodically by the {@link UiRefresher}. Subclasses implement
     * their view-specific refresh logic here.
     */
    protected abstract void onRefresh();

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), this::onRefresh);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }

    /**
     * Overlay info panel positioned on top of the diagram.
     */
    static class DiagramViewInfo extends Div {
        DiagramViewInfo(Component... content) {
            getStyle().setPosition(Style.Position.ABSOLUTE);
            getStyle().set("font-style", "italic");
            setMaxWidth("250px");
            for (var c : content) {
                add(c);
            }
        }

        public DiagramViewInfo(String text) {
            this(new Paragraph(text));
        }
    }
}
