package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.components.RichText;

import java.lang.management.ManagementFactory;

@Route
@MenuItem(order = MenuItem.BEGINNING, icon = VaadinIcon.HOME, title = "J-Smoker")
public class MainView extends AbstractDiagramView {

    private final SystemMonitor systemMonitor = new SystemMonitor();

    public MainView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        super(smokerHardware, uiRefresher);

        add(new DiagramViewInfo(
                new RichText().withMarkDown("""
                    Wellcome to J-Smoker, hello Frank! Powered by Pi4J, Vaadin, Quarkus.
                    [Source code](https://github.com/mstahv/j-smoker).
                    """),
                new Paragraph("Running on: " + smokerHardware.boardName()))
        );
        add(systemMonitor);
        updateDiagram();
        systemMonitor.update();
    }

    @Override
    protected void onRefresh(java.util.List<AppEvent> events) {
        updateDiagram();
        systemMonitor.update();
    }

    static class SystemMonitor extends Div {

        private final StatBadge uptimeLabel = new StatBadge("Uptime");
        private final StatBadge versionLabel = new StatBadge("Version");
        private final StatBadge heapUsage = new StatBadge("Heap", "%s / %s");
        private final StatBadge heapMax = new StatBadge("Heap max");
        private final StatBadge processMemory = new StatBadge("Process RES");
        private final StatBadge osMemory = new StatBadge("OS mem", "%s / %s");
        private final StatBadge cpuUsage = new StatBadge("CPU", "%.0f%% proc / %.0f%% sys");
        private final StatBadge wifiSignal = new StatBadge("WiFi");
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

            add(new StatGrid(uptimeLabel, versionLabel, heapUsage, heapMax, processMemory, osMemory, cpuUsage, wifiSignal), gcButton);
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

            wifiSignal.setValue(readWifiSignal());
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

        /**
         * Read WiFi signal level from /proc/net/wireless.
         * Format: "iface: status link level noise ..."
         * Level is typically in dBm (e.g. -45).
         */
        private String readWifiSignal() {
            try {
                for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/net/wireless"))) {
                    line = line.trim();
                    if (line.startsWith("wlan")) {
                        // "wlan0: 0000 70. -40. -256 ..."
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 4) {
                            String level = parts[3].replace(".", "");
                            int dbm = Integer.parseInt(level);
                            String quality;
                            if (dbm >= -50) quality = "Excellent";
                            else if (dbm >= -60) quality = "Good";
                            else if (dbm >= -70) quality = "Fair";
                            else quality = "Weak";
                            return "%d dBm (%s)".formatted(dbm, quality);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return "N/A";
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
