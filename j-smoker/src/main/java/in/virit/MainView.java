package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.RichText;

import java.lang.management.ManagementFactory;

@Route
@MenuItem(order = MenuItem.BEGINNING, icon = VaadinIcon.HOME, title = "J-Smoker")
public class MainView extends VerticalLayout {

    private final SmokerHardware smokerHardware;
    private final UiRefresher uiRefresher;
    private final AirflowDiagram diagram = new AirflowDiagram();
    private final SystemMonitor systemMonitor = new SystemMonitor();

    public MainView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.smokerHardware = smokerHardware;
        this.uiRefresher = uiRefresher;

        add(new Div(){{
            add(new Paragraph("Running on: " + smokerHardware.boardName()));
            add(new RichText().withMarkDown("""
            Raspberry Pi based BBQ smoker system, powered by Pi4J, Vaadin, Quarkus.
            [Source code](https://github.com/mstahv/j-smoker). 
            """));
            getStyle().setPosition(Style.Position.ABSOLUTE);
            setMaxWidth("300px");
        }});
        add(diagram);
        add(systemMonitor);
        updateDiagram();
        systemMonitor.update();
    }

    private void updateDiagram() {
        diagram.setThrottlePercent(smokerHardware.getThrottlePercent());
        int blower = smokerHardware.getBlowerPercent();
        diagram.setBlowerSpeed(blower);
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
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), () -> {
            updateDiagram();
            systemMonitor.update();
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }

    static class SystemMonitor extends Div {

        private final StatBadge uptimeLabel = new StatBadge("Uptime");
        private final StatBadge versionLabel = new StatBadge("Version");
        private final StatBadge heapUsage = new StatBadge("Heap", "%s / %s");
        private final StatBadge heapMax = new StatBadge("Heap max");
        private final StatBadge processMemory = new StatBadge("Process RES");
        private final StatBadge osMemory = new StatBadge("OS mem", "%s / %s");
        private final StatBadge cpuUsage = new StatBadge("CPU", "%.0f%% proc / %.0f%% sys");
        private final com.sun.management.OperatingSystemMXBean osMx =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        private final long startTimeMillis = ManagementFactory.getRuntimeMXBean().getStartTime();

        SystemMonitor() {
            add(new H4("System monitor"));

            versionLabel.setValue(readAppVersion());

            var gcButton = new Button("Run GC", e -> {
                System.gc();
                update();
            });
            gcButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            add(new StatGrid(uptimeLabel, versionLabel, heapUsage, heapMax, processMemory, osMemory, cpuUsage), gcButton);
        }

        void update() {
            long uptimeMs = System.currentTimeMillis() - startTimeMillis;
            long uptimeSec = uptimeMs / 1000;
            long days = uptimeSec / 86400;
            long hours = (uptimeSec % 86400) / 3600;
            long minutes = (uptimeSec % 3600) / 60;
            uptimeLabel.setValue(days > 0 ? "%dd %dh %dm".formatted(days, hours, minutes)
                    : hours > 0 ? "%dh %dm".formatted(hours, minutes)
                    : "%dm".formatted(minutes));

            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            heapUsage.setValue(mb(used), mb(rt.totalMemory()));
            heapMax.setValue(mb(max));

            processMemory.setValue(mb(readRssBytes()));

            long totalOs = osMx.getTotalMemorySize();
            long freeOs = osMx.getFreeMemorySize();
            osMemory.setValue(mb(totalOs - freeOs), mb(totalOs));

            double cpuLoad = osMx.getProcessCpuLoad();
            double systemLoad = osMx.getSystemCpuLoad();
            cpuUsage.setValue(cpuLoad * 100, systemLoad * 100);
        }

        private String mb(long bytes) {
            return "%dM".formatted(bytes / (1024 * 1024));
        }

        private String readAppVersion() {
            try {
                var jarPath = MainView.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                var modified = java.nio.file.Files.getLastModifiedTime(java.nio.file.Path.of(jarPath));
                return modified.toInstant().atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception e) {
                return "dev";
            }
        }

        private long readRssBytes() {
            try {
                for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/status"))) {
                    if (line.startsWith("VmRSS:")) {
                        return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024;
                    }
                }
            } catch (Exception ignored) {
            }
            return -1;
        }
    }
}
