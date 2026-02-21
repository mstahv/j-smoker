package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class UiRefresher {

    private final Map<UI, Command> listeners = new ConcurrentHashMap<>();

    public void register(UI ui, Command callback) {
        listeners.put(ui, callback);
    }

    public void unregister(UI ui) {
        listeners.remove(ui);
    }

    @Scheduled(every = "5s")
    void refresh() {
        listeners.forEach((ui, callback) -> {
            if (ui.isAttached()) {
                ui.access(callback);
            } else {
                listeners.remove(ui);
            }
        });
    }
}
