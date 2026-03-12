package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import in.virit.SmokerHardware.TemperatureReading;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.style.LumoProps;
import org.vaadin.svgvis.SvgSparkLine;
import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route
@MenuItem(icon = VaadinIcon.ALARM)
public class ThermometersView extends VVerticalLayout {

    private final SmokerHardware smokerHardware;
    private final UiRefresher uiRefresher;
    private final ProbeDisplay probeDisplay;
    private final ProbeDisplay chipDisplay;
    private final ProbeDisplay ibbq1Display;
    private final ProbeDisplay ibbq2Display;
    private final ProbeDisplay ibbq3Display;
    private final WarningMessage ibbqWarning = new WarningMessage("");
    private final Button ibbqReconnectButton = new Button("Reconnect iBBQ", VaadinIcon.REFRESH.create());
    private final WarningMessage meaterWarning = new WarningMessage("");
    private final FormLayout displays = new FormLayout();
    private final Map<String, ProbeDisplay> meaterDisplays = new LinkedHashMap<>();

    public ThermometersView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;

        probeDisplay = new ProbeDisplay("Fire chamber probe", -10, 600);
        chipDisplay = new ProbeDisplay("Chip", -10, 80);
        ibbq1Display = new ProbeDisplay("iBBQ 1 (food chamber)", -10, 250);
        ibbq2Display = new ProbeDisplay("iBBQ 2 (food 1)", 0, 120);
        ibbq3Display = new ProbeDisplay("iBBQ 3 (food 2)", 0, 120);

        ibbqReconnectButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        ibbqReconnectButton.addClickListener(e -> {
            smokerHardware.reconnectIbbq();
            ibbqReconnectButton.setEnabled(false);
            ibbqReconnectButton.setText("Scanning...");
        });

        var ibbqBar = new HorizontalLayout(ibbqWarning, ibbqReconnectButton) {{
            setAlignItems(Alignment.CENTER);
        }};
        add(ibbqBar, meaterWarning);
        displays.add(ibbq1Display, ibbq2Display, ibbq3Display, probeDisplay, chipDisplay);
        add(displays);

        updateReadings();
    }

    private void updateReadings() {
        probeDisplay.update(smokerHardware.getHistory(SmokerHardware.PROBE));
        chipDisplay.update(smokerHardware.getHistory(SmokerHardware.CHIP));
        ibbq1Display.update(smokerHardware.getHistory(SmokerHardware.IBBQ_1));
        ibbq2Display.update(smokerHardware.getHistory(SmokerHardware.IBBQ_2));
        ibbq3Display.update(smokerHardware.getHistory(SmokerHardware.IBBQ_3));
        updateIbbqWarning();
        updateMeaterDisplays();
    }

    private void updateMeaterDisplays() {
        if (smokerHardware.isDevMode() || smokerHardware.isMeaterAvailable()) {
            meaterWarning.setVisible(false);
        } else if (!smokerHardware.isMeaterConnectionAttempted()) {
            meaterWarning.setText("Meater Cloud connecting...");
            meaterWarning.setVisible(true);
        } else {
            meaterWarning.setText("Meater Cloud not connected — check MEATER_EMAIL/MEATER_PASSWORD");
            meaterWarning.setVisible(true);
        }

        for (String key : smokerHardware.getMeaterKeys()) {
            ProbeDisplay display = meaterDisplays.get(key);
            if (display == null) {
                boolean isAmbient = key.contains("(ambient)");
                display = new ProbeDisplay(key, isAmbient ? 0 : 0, isAmbient ? 400 : 120);
                meaterDisplays.put(key, display);
                displays.add(display);
            }
            display.update(smokerHardware.getHistory(key));
        }
    }

    private void updateIbbqWarning() {
        if (smokerHardware.isDevMode() || smokerHardware.isIbbqAvailable()) {
            ibbqWarning.setVisible(false);
            ibbqReconnectButton.setVisible(false);
        } else if (!smokerHardware.isIbbqConnectionAttempted()) {
            ibbqWarning.setText("iBBQ thermometer scanning...");
            ibbqWarning.setVisible(true);
            ibbqReconnectButton.setVisible(false);
        } else {
            ibbqWarning.setText("iBBQ thermometer not connected — BLE device not found");
            ibbqWarning.setVisible(true);
            ibbqReconnectButton.setVisible(true);
            ibbqReconnectButton.setEnabled(true);
            ibbqReconnectButton.setText("Reconnect iBBQ");
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        if (smokerHardware.isDevMode()) {
            Notification.show("Running in dev mode — displaying fake data", 5000, Notification.Position.BOTTOM_START);
        }
        uiRefresher.register(attachEvent.getUI(), this::updateReadings);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }

    static class ProbeDisplay extends VerticalLayout {
        private final Gauge gauge;
        private final RelativeTime timeLabel = new RelativeTime();
        private final SvgSparkLine sparkLine = new SvgSparkLine(300, 80);
        private final Span notConnected = new Span("Not connected") {{
            getStyle()
                    .setColor(LumoProps.SECONDARY_TEXT_COLOR.var())
                    .set("font-style", "italic");
        }};

        ProbeDisplay(String name, double min, double max) {
            gauge = new Gauge() {{
                setMinValue(min);
                setMaxValue(max);
            }};
            timeLabel.getElement().getStyle()
                    .setFontSize(LumoProps.FONT_SIZE_XS.var())
                    .setColor(LumoProps.SECONDARY_TEXT_COLOR.var());
            gauge.setVisible(false);
            sparkLine.setVisible(false);
            add(new HorizontalFloatLayout(new Span(name), timeLabel), notConnected, gauge, sparkLine);
        }

        void update(List<TemperatureReading> history) {
            boolean hasData = !history.isEmpty();
            notConnected.setVisible(!hasData);
            gauge.setVisible(hasData);
            sparkLine.setVisible(hasData);
            if (!hasData) return;
            TemperatureReading latest = history.getLast();
            gauge.setValue(latest.temperature());
            timeLabel.setDatetime(latest.timestamp());
            sparkLine.setData(history.stream()
                    .map(r -> DataPoint.of(r.timestamp(), r.temperature()))
                    .toList());
            sparkLine.draw();
        }
    }
}
