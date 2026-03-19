package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Element;
import org.vaadin.firitin.fluency.ui.FluentHasStyle;
import org.vaadin.firitin.util.VStyle;
import org.vaadin.firitin.util.VStyleUtil;
import org.vaadin.firitin.util.VStyles;
import org.vaadin.firitin.util.style.AuraProps;
import org.vaadin.firitin.util.style.VaadinCssProps;

/**
 * A styled Span used as a read-only status badge in dashboard grids.
 * Has a fixed title prefix and a separately updatable value element.
 * Optionally takes a format string so callers can just pass raw values.
 */
@Tag("stat-badge")
class StatBadge extends Component implements FluentHasStyle {

    private final String format;
    private final Element valueEl = new Element("span");

    StatBadge(String title) {
        this(title, null);
    }

    StatBadge(String title, String format) {
        this.format = format;
        getElement().setText(title + ": ");
        getElement().appendChild(valueEl);
    }

    void setValue(String text) {
        valueEl.setText(text);
    }

    void setValue(Object... args) {
        valueEl.setText(format.formatted(args));
    }

    private static VStyle baseStyle = new VStyle() {{
        setPadding(VaadinCssProps.PADDING_XS.var() + " " + VaadinCssProps.PADDING_S.var());
        setBackground(AuraProps.SURFACE_COLOR.var());
        setBorderRadius(VaadinCssProps.RADIUS_S.var());
        set("font-family", "monospace");
        setFontSize(AuraProps.FONT_SIZE_S.var());
    }};

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        VStyleUtil.injectAsFirst(baseStyle.toCss("stat-badge"));
    }
}
