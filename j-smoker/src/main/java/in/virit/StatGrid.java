package in.virit;

import com.vaadin.flow.component.Component;
import org.vaadin.firitin.components.cssgrid.CssGrid;
import org.vaadin.firitin.util.style.VaadinCssProps;

/**
 * A two-column CSS grid layout for displaying {@link StatBadge} items.
 * Uses a compact gap by default.
 */
class StatGrid extends CssGrid {

    StatGrid(Component... children) {
        super(2);
        setGap(VaadinCssProps.GAP_XS.var());
        for (var child : children) {
            add(child);
        }
    }
}
