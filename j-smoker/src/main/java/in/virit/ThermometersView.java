package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import in.virit.SmokerHardware.TemperatureReading;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.style.VaadinCssProps;
import org.vaadin.svgvis.SvgSparkLine;
import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final HorizontalFloatLayout foodDisplays = new HorizontalFloatLayout();
    private final HorizontalFloatLayout ambientDisplays = new HorizontalFloatLayout();
    private final HorizontalFloatLayout otherDisplays = new HorizontalFloatLayout();
    private final Map<String, ProbeDisplay> meaterDisplays = new LinkedHashMap<>();

    public ThermometersView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;

        probeDisplay = new ProbeDisplay("Fire box probe", -10, 600);
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

        foodDisplays.add(ibbq2Display, ibbq3Display);
        ambientDisplays.add(ibbq1Display);
        otherDisplays.add(probeDisplay, chipDisplay);

        add(new H2("Food"), foodDisplays,
                new H2("Food Chamber Ambient"), ambientDisplays,
                new H2("Other"), otherDisplays);

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
            meaterWarning.hide();
        } else if (!smokerHardware.isMeaterConnectionAttempted()) {
            meaterWarning.showText("Meater Cloud connecting...");
        } else {
            meaterWarning.showText("Meater Cloud not connected — check MEATER_EMAIL/MEATER_PASSWORD");
        }

        for (String key : smokerHardware.getMeaterKeys()) {
            ProbeDisplay display = meaterDisplays.get(key);
            if (display == null) {
                boolean isAmbient = key.contains("(ambient)");
                display = new ProbeDisplay(key, isAmbient ? 0 : 0, isAmbient ? 400 : 120);
                meaterDisplays.put(key, display);
                if (isAmbient) {
                    ambientDisplays.add(display);
                } else {
                    foodDisplays.add(display);
                }
            }
            display.update(smokerHardware.getHistory(key));
        }
    }

    private void updateIbbqWarning() {
        if (smokerHardware.isDevMode() || smokerHardware.isIbbqAvailable()) {
            ibbqWarning.hide();
            ibbqReconnectButton.setVisible(false);
        } else if (!smokerHardware.isIbbqConnectionAttempted()) {
            ibbqWarning.showText("iBBQ thermometer scanning...");
            ibbqReconnectButton.setVisible(false);
        } else {
            ibbqWarning.showText("iBBQ thermometer not connected — BLE device not found");
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
        refresherRegistration = uiRefresher.register(attachEvent.getUI(), events -> updateReadings());
    }

    private Registration refresherRegistration;

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (refresherRegistration != null) {
            refresherRegistration.remove();
            refresherRegistration = null;
        }
    }

    static class ProbeDisplay extends Card {

        private final Gauge gauge;
        private final RelativeTime timeLabel = new RelativeTime();
        private final SvgSparkLine sparkLine = new SvgSparkLine(300, 80);
        private final Span notConnected = new Span("Not connected") {{
            getStyle()
                    .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var())
                    .set("font-style", "italic");
        }};

        ProbeDisplay(String name, double min, double max) {
            Avatar avatar = new Avatar(name);
            if(name.toLowerCase().contains("meater")) {
                avatar.setImage("/meater-probe.svg");
            } else if(name.toLowerCase().contains("food-chamber")) {
                avatar.setImage("/ibbq-door-probe.svg");
            } else if(name.toLowerCase().contains("chip")) {
                avatar.setImage("/thermocouple-chip.svg");
            } else {
                avatar.setImage("/ibbq-probe.svg");
            }
            avatar.setThemeVariants(AvatarVariant.XLARGE);
            setHeaderPrefix(avatar);

            setTitle(name);
            setHeaderSuffix(timeLabel);

            gauge = new Gauge() {{
                setMinValue(min);
                setMaxValue(max);
                setVisible(false);
                getElement().getStyle().setHeight("180px");
                getElement().getStyle().setDisplay(Style.Display.BLOCK);
                getElement().getStyle().setBorderRadius(VaadinCssProps.RADIUS_M.var());
            }};
            setMedia(gauge);
            addThemeVariants(CardVariant.COVER_MEDIA);
            sparkLine.setVisible(false);
            add(new VVerticalLayout(notConnected, sparkLine).withPadding(false));
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
            DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
            sparkLine.setTimeScale(tf.format(history.getFirst().timestamp()), tf.format(latest.timestamp()));
            sparkLine.draw();
        }
    }
}
