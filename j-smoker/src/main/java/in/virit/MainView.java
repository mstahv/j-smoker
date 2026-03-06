package in.virit;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;

@Route
@MenuItem(order = MenuItem.BEGINNING, icon = VaadinIcon.HOME, title = "J-Smoker")
public class MainView extends VerticalLayout {
    public MainView(SmokerHardware smokerHardware) {
        add(new Paragraph(
                "Raspberry Pi-based smoker control system. "
                + "Monitor burning chamber and chip temperatures with live gauges, "
                + "and control airflow via the throttle valve and blower."
        ));
        add(new Paragraph("Running on: " + smokerHardware.boardName()));
    }
}
