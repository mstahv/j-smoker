package in.virit;

import com.vaadin.flow.component.UI;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class UiRefresher {

    private static final Logger LOG = Logger.getLogger(UiRefresher.class.getName());
    private static final int MAX_BUFFERED_EVENTS = 100;
    // If no request from the browser in this many ms, consider the UI dead
    private static final long STALE_THRESHOLD_MS = 60_000;

    private final Map<UI, Consumer<List<AppEvent>>> listeners = new ConcurrentHashMap<>();
    private final List<AppEvent> eventBuffer = new ArrayList<>();

    public void register(UI ui, Consumer<List<AppEvent>> callback) {
        listeners.put(ui, callback);
    }

    public void unregister(UI ui) {
        listeners.remove(ui);
    }

    void onAppEvent(@Observes AppEvent event) {
        synchronized (eventBuffer) {
            if (eventBuffer.size() < MAX_BUFFERED_EVENTS) {
                eventBuffer.add(event);
            }
        }
    }

    @Scheduled(every = "5s")
    void refresh() {
        List<AppEvent> events;
        synchronized (eventBuffer) {
            events = List.copyOf(eventBuffer);
            eventBuffer.clear();
        }

        long now = System.currentTimeMillis();
        var stale = new ArrayList<UI>();

        listeners.forEach((ui, callback) -> {
            if (!ui.isAttached()) {
                stale.add(ui);
                return;
            }

            try {
                ui.access(() -> {
                    long lastRequest = ui.getSession().getLastRequestTimestamp();
                    long silenceMs = now - lastRequest;
                    if (silenceMs > STALE_THRESHOLD_MS) {
                        LOG.warning("UI %s silent for %ds, requesting reload".formatted(
                                ui.getUIId(), silenceMs / 1000));
                        ui.getPage().reload();
                        stale.add(ui);
                        return;
                    }
                    callback.accept(events);
                });
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Removing failed UI from refresh list", e);
                stale.add(ui);
            }
        });

        stale.forEach(listeners::remove);
    }
}
