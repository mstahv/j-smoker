package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.theme.aura.Aura;
import org.vaadin.firitin.appframework.MainLayout;

@Layout
@StyleSheet(Aura.STYLESHEET)
public class TopLayout extends MainLayout {

    private final UiRefresher uiRefresher;

    public TopLayout(UiRefresher uiRefresher) {
        this.uiRefresher = uiRefresher;
    }

    @Override
    protected Object getDrawerHeader() {
        return new Image("logo.svg", "J-Smoker"){{
            getStyle().setDisplay(Style.Display.BLOCK);
            getStyle().setMarginLeft("auto");
            getStyle().setMarginRight("auto");
            setWidth("160px");
        }};
    }

    @Override
    protected void addDrawerContent() {
        super.addDrawerContent();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // slider thumbs are too small for touch devices, let's just re-size them all
        getStyle().set("--vaadin-slider-thumb-height", "2em");
        getStyle().set("--vaadin-slider-thumb-width", "2em");
        attachEvent.getUI().addClassName("aura-accent-purple");

        // addToNavbar (unlike addNavbarHelper) survives view navigation
        if (statusIndicator == null) {
            statusIndicator = new ServerStatusIndicator(uiRefresher);
            addToNavbar(statusIndicator);
        }
    }

    private ServerStatusIndicator statusIndicator;
}
