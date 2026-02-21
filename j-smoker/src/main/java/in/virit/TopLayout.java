package in.virit;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.theme.lumo.Lumo;
import org.vaadin.firitin.appframework.MainLayout;

@Layout
@StyleSheet(Lumo.STYLESHEET)
public class TopLayout extends MainLayout {
    @Override
    protected Object getDrawerHeader() {
        return "J-Smoker";
    }
}
