package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;
import org.vaadin.firitin.util.style.AuraProps;
import org.vaadin.firitin.util.style.VaadinCssProps;

import java.lang.management.ManagementFactory;

@Route
@MenuItem(order = MenuItem.BEGINNING, icon = VaadinIcon.HOME, title = "J-Smoker")
public class MainView extends VerticalLayout {

    private final UiRefresher uiRefresher;
    private final SystemMonitor systemMonitor = new SystemMonitor();

    public MainView(SmokerHardware smokerHardware, UiRefresher uiRefresher) {
        this.uiRefresher = uiRefresher;
        add(new Paragraph(
                "Raspberry Pi-based smoker control system. "
                + "Monitor burning chamber and chip temperatures with live gauges, "
                + "and control airflow via the throttle valve and blower."
        ));
        add(new Paragraph("Running on: " + smokerHardware.boardName()));
        add(systemMonitor);
        systemMonitor.update();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        uiRefresher.register(attachEvent.getUI(), systemMonitor::update);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiRefresher.unregister(detachEvent.getUI());
    }

    static class SystemMonitor extends Div {

        private final Span uptimeLabel = new Span();
        private final Span versionLabel = new Span();
        private final Span heapUsage = new Span();
        private final Span heapMax = new Span();
        private final Span processMemory = new Span();
        private final Span osMemory = new Span();
        private final Span cpuUsage = new Span();
        private final com.sun.management.OperatingSystemMXBean osMx =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        private final long startTimeMillis = ManagementFactory.getRuntimeMXBean().getStartTime();

        SystemMonitor() {
            add(new H4("System monitor"));

            versionLabel.setText("Version: " + readAppVersion());

            var gcButton = new Button("Run GC", e -> {
                System.gc();
                update();
            });
            gcButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            var grid = new Div(uptimeLabel, versionLabel, heapUsage, heapMax, processMemory, osMemory, cpuUsage) {{
                getStyle()
                        .setDisplay(com.vaadin.flow.dom.Style.Display.GRID)
                        .set("grid-template-columns", "1fr 1fr")
                        .set("gap", VaadinCssProps.GAP_XS.var());
            }};

            for (var label : new Span[]{uptimeLabel, versionLabel, heapUsage, heapMax, processMemory, osMemory, cpuUsage}) {
                label.getStyle()
                        .setPadding(VaadinCssProps.PADDING_XS.var() + " " + VaadinCssProps.PADDING_S.var())
                        .setBackground(AuraProps.SURFACE_COLOR.var())
                        .setBorderRadius(VaadinCssProps.RADIUS_S.var())
                        .set("font-family", "monospace")
                        .setFontSize(AuraProps.FONT_SIZE_S.var());
            }

            add(grid, gcButton);
        }

        void update() {
            long uptimeMs = System.currentTimeMillis() - startTimeMillis;
            long uptimeSec = uptimeMs / 1000;
            long days = uptimeSec / 86400;
            long hours = (uptimeSec % 86400) / 3600;
            long minutes = (uptimeSec % 3600) / 60;
            uptimeLabel.setText("Uptime: %s".formatted(
                    days > 0 ? "%dd %dh %dm".formatted(days, hours, minutes)
                            : hours > 0 ? "%dh %dm".formatted(hours, minutes)
                            : "%dm".formatted(minutes)));

            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            heapUsage.setText("Heap: %s / %s".formatted(mb(used), mb(rt.totalMemory())));
            heapMax.setText("Heap max: %s".formatted(mb(max)));

            processMemory.setText("Process RES: %s".formatted(mb(readRssBytes())));

            long totalOs = osMx.getTotalMemorySize();
            long freeOs = osMx.getFreeMemorySize();
            osMemory.setText("OS mem: %s / %s".formatted(mb(totalOs - freeOs), mb(totalOs)));

            double cpuLoad = osMx.getProcessCpuLoad();
            double systemLoad = osMx.getSystemCpuLoad();
            cpuUsage.setText("CPU: %.0f%% proc / %.0f%% sys".formatted(cpuLoad * 100, systemLoad * 100));
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
