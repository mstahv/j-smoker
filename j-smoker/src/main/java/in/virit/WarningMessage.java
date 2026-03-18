package in.virit;

import com.vaadin.flow.component.html.Span;
import org.vaadin.firitin.util.style.AuraProps;
import org.vaadin.firitin.util.style.VaadinCssProps;

public class WarningMessage extends Span {

    public WarningMessage(String text) {
        super(text);
        getStyle()
                .setBackground(AuraProps.ORANGE.var())
                .setPadding(VaadinCssProps.PADDING_S.var() + " " + VaadinCssProps.PADDING_M.var())
                .setBorderRadius(VaadinCssProps.RADIUS_M.var());
    }
}
