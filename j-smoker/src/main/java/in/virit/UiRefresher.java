package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class UiRefresher {

    private static final Logger LOG = Logger.getLogger(UiRefresher.class.getName());
    private static final int MAX_BUFFERED_EVENTS = 100;
    // If the browser hasn't sent a request in this many ms, log it for observability.
    // NOTE: getLastRequestTimestamp() is only bumped by browser->server traffic
    // (user interaction and heartbeats, default heartbeat interval = 300s). Server
    // push does NOT update it, so a healthy idle UI routinely exceeds this. This is
    // therefore a logging signal only — we no longer force a reload based on it.
    private static final long STALE_THRESHOLD_MS = 60_000;

    // Multiple listeners per UI: a view and the navbar status indicator (and
    // potentially others) all live in the same UI, so a single-callback-per-UI
    // map would have them overwrite each other.
    private final Map<UI, List<Consumer<List<AppEvent>>>> listeners = new ConcurrentHashMap<>();
    private final List<AppEvent> eventBuffer = new ArrayList<>();

    /**
     * Registers a callback to be invoked on every refresh round for the given UI.
     * @return a registration; call {@link Registration#remove()} (typically in
     *         {@code onDetach}) to stop receiving updates.
     */
    public Registration register(UI ui, Consumer<List<AppEvent>> callback) {
        listeners.computeIfAbsent(ui, k -> new CopyOnWriteArrayList<>()).add(callback);
        return () -> {
            List<Consumer<List<AppEvent>>> callbacks = listeners.get(ui);
            if (callbacks != null) {
                callbacks.remove(callback);
                if (callbacks.isEmpty()) {
                    listeners.remove(ui);
                }
            }
        };
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

        listeners.forEach((ui, callbacks) -> {
            if (!ui.isAttached()) {
                stale.add(ui);
                return;
            }

            try {
                ui.access(() -> {
                    long silenceMs = now - ui.getSession().getLastRequestTimestamp();
                    if (silenceMs > STALE_THRESHOLD_MS) {
                        var push = ui.getInternals().getPushConnection();
                        boolean pushConnected = push != null && push.isConnected();
                        // Push-aware liveness: getLastRequestTimestamp is not bumped by
                        // server->browser push, so high silence alone is normal for idle
                        // UIs. The push connection state is the real signal.
                        LOG.info("UI %s: no browser request for %ds, pushConnected=%b".formatted(
                                ui.getUIId(), silenceMs / 1000, pushConnected));
                    }
                    callbacks.forEach(callback -> callback.accept(events));
                });
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Removing failed UI from refresh list", e);
                stale.add(ui);
            }
        });

        stale.forEach(listeners::remove);
    }
}
