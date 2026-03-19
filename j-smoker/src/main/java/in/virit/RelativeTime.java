package in.virit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import org.vaadin.firitin.fluency.ui.FluentHasStyle;
import org.vaadin.firitin.util.style.AuraProps;
import in.virit.color.NamedColor;
import org.vaadin.firitin.util.style.VaadinCssProps;

import java.time.Duration;
import java.time.Instant;

/**
 * Wrapper for the GitHub relative-time web component.
 * Shows a human-readable relative timestamp (e.g. "3 minutes ago").
 * Automatically highlights in orange when the timestamp becomes stale
 * (default: 3 minutes).
 */
@Tag("relative-time")
@NpmPackage(value = "@github/relative-time-element", version = "5.0.0")
@JsModule("@github/relative-time-element")
public class RelativeTime extends Component implements FluentHasStyle {

    private static final Duration DEFAULT_STALE_THRESHOLD = Duration.ofSeconds(180);

    private Duration staleThreshold = DEFAULT_STALE_THRESHOLD;

    public RelativeTime() {
        getElement().getStyle()
                .setFontSize(AuraProps.FONT_SIZE_XS.var())
                .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var());
    }

    public void setDatetime(Instant instant) {
        getElement().setAttribute("datetime", instant.toString());
        boolean stale = Duration.between(instant, Instant.now()).compareTo(staleThreshold) > 0;
        if(stale) {
            getStyle().setColor(NamedColor.ORANGE);
        } else {
            getStyle().setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var());
        }
    }

    public void setStaleThreshold(Duration staleThreshold) {
        this.staleThreshold = staleThreshold;
    }

    public void setFormat(String format) {
        getElement().setAttribute("format", format);
    }

    public void setPrecision(String precision) {
        getElement().setAttribute("precision", precision);
    }

    public void setThreshold(String threshold) {
        getElement().setAttribute("threshold", threshold);
    }

    public void setFormatStyle(String formatStyle) {
        getElement().setAttribute("formatStyle", formatStyle);
    }
}
