package in.virit;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.theme.aura.Aura;
import org.vaadin.firitin.appframework.MainLayout;

@Layout
@StyleSheet(Aura.STYLESHEET)
public class TopLayout extends MainLayout {
    @Override
    protected Object getDrawerHeader() {
        return new Image("logo.svg", "J-Smoker"){{
            getStyle().setDisplay(Style.Display.BLOCK);
            getStyle().setMarginLeft("auto");
            getStyle().setMarginRight("auto");
            setWidth("160px");
        }};
    }
}
