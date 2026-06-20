package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.Registration;
import in.virit.color.NamedColor;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.ResizeObserver;
import org.vaadin.svgvis.SvgSparkLine;
import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Base class for views that display the {@link AirflowDiagram}.
 * Provides the diagram instance, common hardware-to-diagram sync logic,
 * and automatic UI refresh registration.
 */
public abstract class AbstractDiagramView extends VVerticalLayout {

    private static final int SPARKLINE_WINDOW_SECONDS = 30 * 60;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    protected final AirflowDiagram diagram = new AirflowDiagram();
    protected final SmokerHardware smokerHardware;
    protected final UiRefresher uiRefresher;
    protected final SmokerController controller;

    private final SvgSparkLine fireSparkLine = new SvgSparkLine(140, 60){{
        setWidthFull();
    }};
    private final SvgSparkLine chamberSparkLine = new SvgSparkLine(140, 60){{
        setWidthFull();
    }};
    private final SvgSparkLine foodSparkLine = new SvgSparkLine(140, 60){{
        setWidthFull();
    }};

    protected AbstractDiagramView(SmokerHardware smokerHardware, UiRefresher uiRefresher, SmokerController controller) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;
        this.controller = controller;
        getStyle().setPosition(Style.Position.RELATIVE);

        fireSparkLine.setLineColor(NamedColor.FIREBRICK);
        fireSparkLine.setTitle("Fire");
        chamberSparkLine.setLineColor(NamedColor.DARKORANGE);
        chamberSparkLine.setTitle("Chamber");
        foodSparkLine.setLineColor(NamedColor.FORESTGREEN);
        foodSparkLine.setTitle("Food");

        var sparkLines = new Div();
        ResizeObserver.get().observe(this, dims -> {
            sparkLines.removeAll();
            if(dims.width() > 700) {
                sparkLines.add(new VerticalLayout(chamberSparkLine, foodSparkLine, fireSparkLine){{
                    setSpacing("2em");
                }});
                sparkLines.setWidth(140, Unit.PIXELS);
            } else {
                sparkLines.setWidth(null);
                sparkLines.add(new HorizontalLayout(chamberSparkLine, foodSparkLine, fireSparkLine){{
                    setWidthFull();
                }});
            }
        });


        add(new HorizontalLayout(diagram, sparkLines){{
            setWrap(true);
            setWidthFull();
        }});
    }

    /**
     * Syncs the diagram with current hardware state (throttle, blower, temperatures).
     */
    protected void updateDiagram() {
        diagram.setThrottlePercent(smokerHardware.getThrottlePercent());
        diagram.setBlowerSpeed(smokerHardware.getBlowerPercent());
        if (smokerHardware.isBlowerForceOn()) {
            diagram.setBlowerLabel("Blower FULL");
        } else if (smokerHardware.isBlowerSoftPwmEnabled()) {
            diagram.setBlowerLabel("Blower PWM %d %%".formatted(smokerHardware.getBlowerDutyPercent()));
        } else {
            diagram.setBlowerLabel("Blower OFF");
        }
        var fire = smokerHardware.getLatestReading(SmokerHardware.PROBE);
        if (fire != null) {
            diagram.setFireTemp("%.0f °C".formatted(fire.temperature()));
        }
        var chamber = smokerHardware.getLatestReading(SmokerHardware.IBBQ_1);
        if (chamber != null) {
            diagram.setFoodChamberTemp("%.0f °C".formatted(chamber.temperature()));
        }
        var food = smokerHardware.getLatestReading(SmokerHardware.IBBQ_2);
        if (food != null) {
            diagram.setFoodProbeTemp("%.0f °C".formatted(food.temperature()));
        }

        updateSparkLines();
    }

    private void updateSparkLines() {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(SPARKLINE_WINDOW_SECONDS);

        // Fire
        var fireHistory = filterWindow(smokerHardware.getHistory(SmokerHardware.PROBE), windowStart);
        updateSparkLine(fireSparkLine, fireHistory, windowStart, now);

        // Chamber: iBBQ 1 as primary, meater ambients as additional series
        var ibbq1History = filterWindow(smokerHardware.getHistory(SmokerHardware.IBBQ_1), windowStart);
        chamberSparkLine.setXRange(windowStart, now);
        chamberSparkLine.setData(toDataPoints(ibbq1History));
        // Target chamber temperature as a reference line (only while the controller
        // is running). Reference lines persist across draws, so clear first.
        chamberSparkLine.clearReferenceLines();
        if (controller.getState() != SmokerController.State.OFF) {
            chamberSparkLine.addReferenceLine(controller.getSetpoint(), NamedColor.GRAY, "Tlo");
        }
        for (String key : smokerHardware.getMeaterKeys()) {
            if (!key.contains("(ambient)")) continue;
            var meaterHistory = filterWindow(smokerHardware.getHistory(key), windowStart);
            if (!meaterHistory.isEmpty()) {
                chamberSparkLine.addSeries(toDataPoints(meaterHistory), NamedColor.CORAL);
            }
        }
        setTimeLabels(chamberSparkLine, windowStart, now);
        chamberSparkLine.draw();

        // Food: iBBQ 2 as primary, iBBQ 3 and meater tips as additional
        var food2History = filterWindow(smokerHardware.getHistory(SmokerHardware.IBBQ_2), windowStart);
        foodSparkLine.setXRange(windowStart, now);
        foodSparkLine.setData(toDataPoints(food2History));
        var food3History = filterWindow(smokerHardware.getHistory(SmokerHardware.IBBQ_3), windowStart);
        if (!food3History.isEmpty()) {
            foodSparkLine.addSeries(toDataPoints(food3History), NamedColor.OLIVEDRAB);
        }
        for (String key : smokerHardware.getMeaterKeys()) {
            if (!key.contains("(tip)")) continue;
            var meaterHistory = filterWindow(smokerHardware.getHistory(key), windowStart);
            if (!meaterHistory.isEmpty()) {
                foodSparkLine.addSeries(toDataPoints(meaterHistory), NamedColor.MEDIUMSEAGREEN);
            }
        }
        setTimeLabels(foodSparkLine, windowStart, now);
        foodSparkLine.draw();
    }

    private void updateSparkLine(SvgSparkLine sparkLine, List<SmokerHardware.TemperatureReading> history,
                                 Instant windowStart, Instant now) {
        sparkLine.setXRange(windowStart, now);
        sparkLine.setData(toDataPoints(history));
        setTimeLabels(sparkLine, windowStart, now);
        sparkLine.draw();
    }

    private void setTimeLabels(SvgSparkLine sparkLine, Instant windowStart, Instant now) {
        sparkLine.setTimeScale(TIME_FMT.format(windowStart), TIME_FMT.format(now));
    }

    private static List<SmokerHardware.TemperatureReading> filterWindow(
            List<SmokerHardware.TemperatureReading> history, Instant windowStart) {
        return history.stream()
                .filter(r -> !r.timestamp().isBefore(windowStart))
                .toList();
    }

    private static List<DataPoint> toDataPoints(List<SmokerHardware.TemperatureReading> readings) {
        return readings.stream()
                .map(r -> DataPoint.of(r.timestamp(), r.temperature()))
                .toList();
    }

    static String chamberSourceSuffix(String key) {
        if (SmokerHardware.IBBQ_1.equals(key)) return "ibbq";
        if (key != null && key.startsWith(SmokerHardware.MEATER_PREFIX)) {
            // "Meater 1 (ambient)" → "m1", "Meater 2 (ambient)" → "m2"
            String num = key.replace(SmokerHardware.MEATER_PREFIX, "").split(" ")[0];
            return "m" + num;
        }
        return "?";
    }

    /**
     * Called periodically by the {@link UiRefresher}. Subclasses implement
     * their view-specific refresh and event handling logic here.
     */
    protected abstract void onRefresh(List<AppEvent> events);

    private Registration refresherRegistration;

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        refresherRegistration = uiRefresher.register(attachEvent.getUI(), this::onRefresh);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (refresherRegistration != null) {
            refresherRegistration.remove();
            refresherRegistration = null;
        }
    }

    /**
     * Overlay info panel positioned on top of the diagram.
     */
    static class DiagramViewInfo extends Div {
        DiagramViewInfo(Component... content) {
            getStyle().setPosition(Style.Position.ABSOLUTE);
            getStyle().set("font-style", "italic");
            setMaxWidth("250px");
            for (var c : content) {
                add(c);
            }
        }

        public DiagramViewInfo(String text) {
            this(new Paragraph(text));
        }
    }
}
