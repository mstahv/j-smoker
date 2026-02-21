package in.virit;

import com.vaadin.flow.component.html.Span;
import org.vaadin.firitin.util.style.LumoProps;

public class WarningMessage extends Span {

    public WarningMessage(String text) {
        super(text);
        getStyle()
                .setBackground(LumoProps.WARNING_COLOR_10PCT.var())
                .setColor(LumoProps.WARNING_TEXT_COLOR.var())
                .setPadding(LumoProps.SPACE_S.var() + " " + LumoProps.SPACE_M.var())
                .setBorderRadius(LumoProps.BORDER_RADIUS_M.var())
                .setWidth("100%");
    }
}
