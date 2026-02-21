package in.virit;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;

@Route
@MenuItem(icon = VaadinIcon.MAGIC)
public class AutomaticView extends VerticalLayout {

    public AutomaticView() {
        add(new Paragraph("TODO: Automatic temperature control mode"));
    }
}
