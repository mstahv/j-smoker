package in.virit;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Quarkus-flavoured {@link LiveUpdater} for the smoker app: drives the update
 * cycle from a {@code @Scheduled} method and feeds {@link AppEvent}s (fired as
 * CDI events) into the push delivery. All the reachability/skip logic lives in
 * {@link LiveUpdater}.
 */
@ApplicationScoped
public class UiRefresher extends LiveUpdater<AppEvent> {

    @Scheduled(every = "5s")
    void refresh() {
        tick();
    }

    void onAppEvent(@Observes AppEvent event) {
        publish(event);
    }
}
