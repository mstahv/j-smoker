package in.virit;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A self-contained {@link LiveUpdater} that drives itself with a JDK
 * {@link ScheduledExecutorService} daemon thread. Use this when no framework
 * scheduler is available (plain servlet apps, tests, demos):
 *
 * <pre>{@code
 * var updater = new ScheduledLiveUpdater<MyEvent>(Duration.ofSeconds(5));
 * // in a view:
 * reg = updater.subscribe(ui, events -> refreshFromBackend());
 * // on shutdown:
 * updater.close();
 * }</pre>
 *
 * In a managed environment (Quarkus/Spring) prefer subclassing {@link LiveUpdater}
 * and calling {@link #tick()} from a {@code @Scheduled} method, so the container
 * owns the lifecycle and thread pool.
 *
 * @param <E> see {@link LiveUpdater}
 */
public class ScheduledLiveUpdater<E> extends LiveUpdater<E> implements AutoCloseable {

    private final ScheduledExecutorService executor;

    public ScheduledLiveUpdater(Duration interval) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-updater");
            thread.setDaemon(true);
            return thread;
        });
        // Fixed delay (not fixed rate) so a slow tick cannot pile up invocations.
        executor.scheduleWithFixedDelay(this::tick,
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
