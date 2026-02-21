package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import in.virit.SmokerHardware.TemperatureReading;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.svgvis.SvgSparkLine;
import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route
public class ThermometersView extends VVerticalLayout {

    private final SmokerHardware smokerHardware;
    private final UiRefresher uiRefresher;
    private final ProbeDisplay probeDisplay;
    private final ProbeDisplay chipDisplay;

    public ThermometersView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;

        probeDisplay = new ProbeDisplay("Probe", -10, 600);
        chipDisplay = new ProbeDisplay("Chip", -10, 80);

        add(new Button(VaadinIcon.REFRESH.create(), e -> updateReadings()));
        add(new HorizontalLayout(probeDisplay, chipDisplay));

        updateReadings();
    }

    private void updateReadings() {
        probeDisplay.update(smokerHardware.getHistory(SmokerHardware.PROBE));
        chipDisplay.update(smokerHardware.getHistory(SmokerHardware.CHIP));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), this::updateReadings);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }

    static class ProbeDisplay extends VerticalLayout {
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        private final Gauge gauge;
        private final Span readingLabel = new Span();
        private final Span timeLabel = new Span();
        private final SvgSparkLine sparkLine = new SvgSparkLine(300, 80);

        ProbeDisplay(String name, double min, double max) {
            gauge = new Gauge() {{
                setMinValue(min);
                setMaxValue(max);
            }};
            readingLabel.getStyle()
                    .setFontSize("var(--lumo-font-size-xl)")
                    .setFontWeight("bold");
            timeLabel.getStyle()
                    .setFontSize("var(--lumo-font-size-s)")
                    .setColor("var(--lumo-secondary-text-color)");
            add(new Span(name), gauge, readingLabel, timeLabel, sparkLine);
        }

        void update(List<TemperatureReading> history) {
            if (history.isEmpty()) return;
            TemperatureReading latest = history.getLast();
            gauge.setValue(latest.temperature());
            readingLabel.setText(String.format("%.1f °C", latest.temperature()));
            timeLabel.setText(TIME_FMT.format(latest.timestamp()));
            sparkLine.setData(history.stream()
                    .map(r -> DataPoint.of(r.timestamp(), r.temperature()))
                    .toList());
            sparkLine.draw();
        }
    }
}
