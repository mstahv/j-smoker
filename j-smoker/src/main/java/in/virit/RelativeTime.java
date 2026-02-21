package in.virit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

import java.time.Instant;

@Tag("relative-time")
@NpmPackage(value = "@github/relative-time-element", version = "5.0.0")
@JsModule("@github/relative-time-element")
public class RelativeTime extends Component {

    public void setDatetime(Instant instant) {
        getElement().setAttribute("datetime", instant.toString());
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
