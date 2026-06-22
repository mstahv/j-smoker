package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically updates Vaadin UIs from backend state via server push — but only
 * those that can actually receive the update right now.
 * <p>
 * The problem this solves: when a client vanishes ungracefully (phone/tab closed
 * or asleep) the server-side {@link UI} stays alive for a while. Naively pushing
 * to it on every cycle buffers undelivered changes (pending invocations / resync
 * backlog), which leaks heap and floods the client with a burst of stale updates
 * when it reconnects. This component checks reachability before generating any
 * update and simply skips unreachable UIs, resuming automatically when they
 * return.
 * <p>
 * Two ways to use it:
 * <ul>
 *   <li><b>Pull</b> — register a callback that reads current backend state and
 *       updates the UI; ignore the event list. Inherently backpressure-friendly.</li>
 *   <li><b>Events</b> — additionally {@link #publish(Object) publish} discrete
 *       events; each cycle the batch fired since the previous cycle is delivered
 *       to reachable UIs (and dropped for unreachable ones).</li>
 * </ul>
 * The class is framework-agnostic. It does not schedule itself: call {@link #tick()}
 * from a scheduler. See {@link ScheduledLiveUpdater} for a self-contained
 * JDK-timer variant, or subclass and drive {@code tick()} from a framework
 * scheduler (e.g. Quarkus/Spring {@code @Scheduled}).
 *
 * @param <E> the event type for the optional event-delivery mode (use any type,
 *            e.g. a marker interface; pull-only users can ignore it)
 */
public abstract class LiveUpdater<E> {

    private static final Logger LOG = Logger.getLogger(LiveUpdater.class.getName());

    // Multiple callbacks per UI: e.g. a view and a status indicator share a UI,
    // so a single-callback-per-UI map would have them overwrite each other.
    private final Map<UI, List<Consumer<List<E>>>> subscribers = new ConcurrentHashMap<>();
    // UIs currently considered unreachable; used only to fire transition hooks once.
    private final Set<UI> offlineUis = ConcurrentHashMap.newKeySet();
    private final List<E> eventBuffer = new ArrayList<>();

    private int maxBufferedEvents = 100;
    private int heartbeatTimeoutFactor = 3;

    /**
     * Registers a callback invoked on every reachable {@link #tick()} for the UI.
     * @return a registration; call {@link Registration#remove()} (typically in
     *         {@code onDetach}) to stop receiving updates.
     */
    public Registration subscribe(UI ui, Consumer<List<E>> onUpdate) {
        subscribers.computeIfAbsent(ui, k -> new CopyOnWriteArrayList<>()).add(onUpdate);
        return () -> {
            List<Consumer<List<E>>> callbacks = subscribers.get(ui);
            if (callbacks != null) {
                callbacks.remove(onUpdate);
                if (callbacks.isEmpty()) {
                    subscribers.remove(ui);
                }
            }
        };
    }

    /**
     * Buffers an event for delivery to reachable UIs on the next {@link #tick()}.
     * Events fired while a UI is unreachable are not delivered to it. Buffer is
     * capped at {@link #setMaxBufferedEvents(int)} to bound memory.
     */
    public void publish(E event) {
        synchronized (eventBuffer) {
            if (eventBuffer.size() < maxBufferedEvents) {
                eventBuffer.add(event);
            }
        }
    }

    /**
     * Runs one update cycle: delivers the buffered events (and triggers the pull
     * callbacks) for every reachable UI, skips unreachable ones, and drops UIs
     * that have detached. Safe to call from any thread; work is dispatched to each
     * UI via {@link UI#access}.
     */
    public void tick() {
        List<E> events;
        synchronized (eventBuffer) {
            events = List.copyOf(eventBuffer);
            eventBuffer.clear();
        }

        var stale = new ArrayList<UI>();
        subscribers.forEach((ui, callbacks) -> {
            if (!ui.isAttached()) {
                stale.add(ui);
                offlineUis.remove(ui);
                return;
            }
            try {
                ui.access(() -> {
                    String unreachableReason = unreachableReason(ui);
                    if (unreachableReason == null) {
                        if (offlineUis.remove(ui)) {
                            onReachable(ui);
                        }
                        callbacks.forEach(callback -> callback.accept(events));
                    } else {
                        // Skip generating updates for an unreachable client so we
                        // don't buffer changes that flood it on reconnect / leak
                        // heap. Resumes automatically once it is reachable again.
                        if (offlineUis.add(ui)) {
                            onUnreachable(ui, unreachableReason);
                        }
                    }
                });
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Removing failed UI from live updates", e);
                stale.add(ui);
                offlineUis.remove(ui);
            }
        });
        stale.forEach(subscribers::remove);
    }

    /**
     * Returns why the client behind this UI cannot currently receive pushes, or
     * {@code null} if it is reachable. The push connection state catches a closed
     * channel quickly; a stale heartbeat is the backstop for "zombie" connections
     * that look open server-side but whose client is gone (detection speed there
     * follows the configured heartbeat interval). Override to customize the policy.
     */
    protected String unreachableReason(UI ui) {
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
            long sinceHeartbeat = System.currentTimeMillis() - ui.getInternals().getLastHeartbeatTimestamp();
            if (sinceHeartbeat > (long) heartbeatTimeoutFactor * heartbeatSeconds * 1000) {
                return "no heartbeat for %ds (push still reports connected)".formatted(sinceHeartbeat / 1000);
            }
        }
        return null;
    }

    /** Called (under the UI lock) when a UI transitions back to reachable. */
    protected void onReachable(UI ui) {
        LOG.info("UI %s back online, resuming updates".formatted(uiLabel(ui)));
    }

    /** Called (under the UI lock) when a UI transitions to unreachable. */
    protected void onUnreachable(UI ui, String reason) {
        LOG.info("UI %s unreachable (%s), pausing updates until it returns".formatted(uiLabel(ui), reason));
    }

    /** Max events retained between ticks (older ones are dropped). Default 100. */
    public void setMaxBufferedEvents(int maxBufferedEvents) {
        this.maxBufferedEvents = maxBufferedEvents;
    }

    /**
     * Heartbeat-staleness backstop multiplier: a UI is deemed unreachable if no
     * heartbeat has arrived for {@code factor * heartbeatInterval}. Default 3.
     */
    public void setHeartbeatTimeoutFactor(int heartbeatTimeoutFactor) {
        this.heartbeatTimeoutFactor = heartbeatTimeoutFactor;
    }

    /**
     * A log label distinguishing UIs across sessions: {@code getUIId()} is only
     * unique within a session, so two clients commonly both appear as "UI 0".
     * Appends a short suffix of the HTTP session id to tell them apart.
     */
    protected static String uiLabel(UI ui) {
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
}
