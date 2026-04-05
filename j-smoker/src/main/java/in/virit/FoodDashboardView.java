package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DashStyle;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import in.virit.color.NamedColor;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.html.VImage;
import org.vaadin.firitin.components.html.VSpan;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.style.AuraProps;

import com.vaadin.flow.component.charts.model.Time;

import java.util.LinkedHashMap;
import java.util.Map;

@Route
@MenuItem(icon = VaadinIcon.CUTLERY)
public class FoodDashboardView extends VVerticalLayout {

    private static final SolidColor[] SERIES_COLORS = {
            new SolidColor("#1f77b4"),
            new SolidColor("#ff7f0e"),
            new SolidColor("#2ca02c"),
            new SolidColor("#d62728"),
            new SolidColor("#9467bd")
    };

    private final SmokerHardware hardware;
    private final FoodTargets foodTargets;
    private final UiRefresher uiRefresher;
    private final Map<String, ProbeCard> probeCards = new LinkedHashMap<>();
    private final HorizontalFloatLayout cardsContainer = new HorizontalFloatLayout();
    private final Chart chart;

    public FoodDashboardView(SmokerHardware hardware, FoodTargets foodTargets, UiRefresher uiRefresher) {
        this.hardware = hardware;
        this.foodTargets = foodTargets;
        this.uiRefresher = uiRefresher;

        addProbeCard(SmokerHardware.IBBQ_2);
        addProbeCard(SmokerHardware.IBBQ_3);

        chart = new Chart(ChartType.LINE);
        chart.setHeight(400, Unit.PIXELS);

        add(cardsContainer, chart);
        updateView();
    }

    private void addProbeCard(String probeKey) {
        if (!probeCards.containsKey(probeKey)) {
            var card = new ProbeCard(probeKey, foodTargets);
            probeCards.put(probeKey, card);
            cardsContainer.add(card);
        }
    }

    private void updateView() {
        hardware.getMeaterKeys().stream()
                .filter(k -> k.contains("(tip)"))
                .forEach(this::addProbeCard);

        probeCards.values().forEach(card -> card.update(hardware));
        updateChart();
    }

    private void updateChart() {
        Configuration conf = chart.getConfiguration();
        conf.setTitle("Food Temperature History");
        Time time = new Time();
        // 🤦‍♂️
        //time.setTimezone(TimeZone.getDefault().toZoneId().toString());
        conf.setTime(time);
        conf.getxAxis().setType(AxisType.DATETIME);
        conf.getxAxis().setTitle("Time");
        YAxis yAxis = conf.getyAxis();
        yAxis.setTitle("Temperature (\u00B0C)");
        yAxis.setPlotLines();
        conf.getCredits().setEnabled(false);

        var allSeries = new java.util.ArrayList<com.vaadin.flow.component.charts.model.Series>();
        int colorIndex = 0;
        for (var entry : probeCards.entrySet()) {
            String probeKey = entry.getKey();
            ProbeCard card = entry.getValue();
            SolidColor color = SERIES_COLORS[colorIndex % SERIES_COLORS.length];

            DataSeries series = new DataSeries(probeKey);
            PlotOptionsLine options = new PlotOptionsLine();
            options.setColor(color);
            series.setPlotOptions(options);

            for (var reading : hardware.getHistory(probeKey)) {
                series.add(new DataSeriesItem(
                        reading.timestamp().toEpochMilli(),
                        reading.temperature()));
            }
            allSeries.add(series);

            Double target = card.getTarget();
            if (target != null && target > 0) {
                PlotLine plotLine = new PlotLine();
                plotLine.setValue(target);
                plotLine.setColor(color);
                plotLine.setDashStyle(DashStyle.DASH);
                plotLine.setWidth(2);
                yAxis.addPlotLine(plotLine);
            }

            colorIndex++;
        }

        conf.setSeries(allSeries);
        chart.drawChart();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), events -> updateView());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }

    static class ProbeCard extends Card {

        class TemperatureBadge extends Badge {
            public TemperatureBadge() {
                // TODO complain about the new "number API"...
                //super("°C", null);
                //setNumber(0.0);
                getStyle().setFontSize("1.5em");
            }
        }

        private final String probeKey;
        private final FoodTargets foodTargets;
        private final TemperatureBadge temperatureBadge = new TemperatureBadge();
        private final RelativeTime timeLabel = new RelativeTime() {{
            setStaleThreshold(java.time.Duration.ofMinutes(5));
        }};
        private final NumberField targetField;
        private final VSpan progressLabel = new VSpan();
        private final Span etaLabel = new Span();

        ProbeCard(String probeKey, FoodTargets foodTargets) {
            this.probeKey = probeKey;
            this.foodTargets = foodTargets;
            addThemeVariants(CardVariant.COVER_MEDIA);
            VImage thermoImage;
            if(probeKey.toLowerCase().contains("ibbq")) {
                thermoImage = new VImage("/ibbq-probe.svg","ibbq thermometer");
            } else {
                thermoImage = new VImage("/meater-probe.svg","Meater");
            }
            thermoImage.getStyle().setBackgroundColor(NamedColor.LIGHTGRAY);
            setMedia(thermoImage);
            setTitle(probeKey);

            setHeaderSuffix(new HorizontalLayout(temperatureBadge, timeLabel) {{
                setAlignItems(Alignment.CENTER);
                setSpacing(false);
                getStyle().set("gap", "var(--lumo-space-xs)");
            }});
            Double savedTarget = foodTargets.getTarget(probeKey);
            targetField = new NumberField("Target \u00B0C") {{
                setMin(0);
                setMax(120);
                setStep(1);
                setValue(savedTarget != null ? savedTarget : 85.0);
                setStepButtonsVisible(true);
                setSuffixComponent(new Span("\u00B0C"));
                setWidth("150px");
            }};
            targetField.addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    foodTargets.setTarget(probeKey, e.getValue());
                }
            });

            progressLabel.getStyle().setFontSize(AuraProps.FONT_SIZE_S.var());
            etaLabel.getStyle().setFontSize(AuraProps.FONT_SIZE_S.var());

            add(new VVerticalLayout(targetField, progressLabel, etaLabel).withPadding(false));
        }

        Double getTarget() {
            return targetField.getValue();
        }

        void update(SmokerHardware hardware) {
            // Sync target from shared state (may have been changed on another device)
            Double shared = foodTargets.getTarget(probeKey);
            Double local = targetField.getValue();
            if (shared != null && !shared.equals(local)) {
                targetField.setValue(shared);
            }

            var reading = hardware.getLatestReading(probeKey);
            if (reading == null) {
                temperatureBadge.setText("No data");
                progressLabel.setText("");
                etaLabel.setText("");
                return;
            }

            double current = reading.temperature();
            temperatureBadge.setText("%.1f°".formatted(current));
            timeLabel.setDatetime(reading.timestamp());

            Double target = targetField.getValue();
            if (target == null || target <= 0) {
                progressLabel.setText("No target set");
                etaLabel.setText("");
                return;
            }

            if (current >= target) {
                progressLabel.setText("Target reached!");
                progressLabel.getStyle().setColor(NamedColor.GREEN);
                etaLabel.setText("");
                return;
            }

            double progress = (current / target) * 100;
            progressLabel.setText("%.0f%% of target".formatted(progress));
            progressLabel.getStyle().setColor((String) null);

            double rate = hardware.getTemperatureRate(probeKey, 3600);
            if (rate <= 0) {
                etaLabel.setText("ETA: \u2014 (not rising)");
            } else {
                double ratePerSecond = rate / 3600.0;
                double etaSeconds = (target - current) / ratePerSecond;
                etaLabel.setText("ETA: %s".formatted(formatDuration(etaSeconds)));
            }
        }

        private String formatDuration(double seconds) {
            if (seconds > 24 * 3600) return ">24h";
            int totalMinutes = (int) (seconds / 60);
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            if (hours > 0) {
                return "~%dh %dm".formatted(hours, minutes);
            }
            return "~%dm".formatted(minutes);
        }
    }
}
