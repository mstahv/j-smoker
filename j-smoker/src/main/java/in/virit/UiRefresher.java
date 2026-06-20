package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class UiRefresher {

    private static final Logger LOG = Logger.getLogger(UiRefresher.class.getName());
    private static final int MAX_BUFFERED_EVENTS = 100;

    // Multiple listeners per UI: a view and the navbar status indicator (and
    // potentially others) all live in the same UI, so a single-callback-per-UI
    // map would have them overwrite each other.
    private final Map<UI, List<Consumer<List<AppEvent>>>> listeners = new ConcurrentHashMap<>();
    private final List<AppEvent> eventBuffer = new ArrayList<>();
    // UIs currently considered unreachable; used only to log online/offline
    // transitions once instead of every round.
    private final Set<UI> offlineUis = ConcurrentHashMap.newKeySet();

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
                offlineUis.remove(ui);
                return;
            }

            try {
                ui.access(() -> {
                    String unreachableReason = unreachableReason(ui, now);
                    if (unreachableReason == null) {
                        if (offlineUis.remove(ui)) {
                            LOG.info("UI %s back online, resuming updates".formatted(uiLabel(ui)));
                        }
                        callbacks.forEach(callback -> callback.accept(events));
                    } else {
                        // The client is not currently reachable. Skip generating any
                        // updates: pushing into a dead connection only buffers changes
                        // that flood the client on reconnect and leak heap meanwhile.
                        // We resume automatically once it is reachable again.
                        if (offlineUis.add(ui)) {
                            LOG.info("UI %s unreachable (%s), pausing updates until it returns".formatted(
                                    uiLabel(ui), unreachableReason));
                        }
                    }
                });
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Removing failed UI from refresh list", e);
                stale.add(ui);
                offlineUis.remove(ui);
            }
        });

        stale.forEach(listeners::remove);
    }

    /**
     * A log label distinguishing UIs across sessions: {@code getUIId()} is only
     * unique within a session, so two clients commonly both appear as "UI 0".
     * Appends a short suffix of the HTTP session id to tell them apart.
     */
    private static String uiLabel(UI ui) {
        String session = "";
        try {
            var wrapped = ui.getSession().getSession();
            if (wrapped != null && wrapped.getId() != null) {
                String id = wrapped.getId();
                session = id.length() > 6 ? id.substring(id.length() - 6) : id;
            }
        } catch (Exception ignore) {
            // best-effort label only
        }
        return "%d@%s".formatted(ui.getUIId(), session);
    }

    /**
     * Returns why the client behind this UI cannot currently receive pushes, or
     * {@code null} if it is reachable. The push connection state catches a closed
     * channel quickly; a stale heartbeat is the backstop for "zombie" connections
     * that look open server-side but whose client is gone (detection speed there
     * follows the configured heartbeat interval, so a shorter interval reclaims
     * such UIs faster). The distinct reasons are logged so we can tell which path
     * paused a UI.
     */
    private String unreachableReason(UI ui, long now) {
        var push = ui.getInternals().getPushConnection();
        if (push == null) {
            return "no push connection";
        }
        if (!push.isConnected()) {
            return "push disconnected";
        }
        int heartbeatSeconds = ui.getSession().getService()
                .getDeploymentConfiguration().getHeartbeatInterval();
        if (heartbeatSeconds > 0) {
            long sinceHeartbeat = now - ui.getInternals().getLastHeartbeatTimestamp();
            if (sinceHeartbeat > 3L * heartbeatSeconds * 1000) {
                return "no heartbeat for %ds (push still reports connected)".formatted(sinceHeartbeat / 1000);
            }
        }
        return null;
    }
}
