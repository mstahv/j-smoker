package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.Registration;

/**
 * A small dot for the navbar visualizing the liveness of the server data feed.
 * <p>
 * Each refresh round delivered by {@link UiRefresher} (every ~5s via push) is a
 * "beat": the dot pulses green. The colour is driven entirely client-side by a
 * timer measuring the time since the last beat, so it correctly degrades even
 * when the server can no longer be reached:
 * <ul>
 *     <li>green — fresh data flowing (beat within ~12s)</li>
 *     <li>orange — no data for a while (~12-30s)</li>
 *     <li>red — feed considered down (&gt;30s) or Vaadin reports the connection lost</li>
 * </ul>
 * Vaadin's own {@code window.Vaadin.connectionState} is also consulted so a
 * detected disconnect flips the dot to red immediately instead of waiting for
 * the staleness timer.
 */
public class ServerStatusIndicator extends Span {

    private final UiRefresher uiRefresher;
    private Registration refresherRegistration;

    public ServerStatusIndicator(UiRefresher uiRefresher) {
        this.uiRefresher = uiRefresher;

        Style s = getStyle();
        s.setWidth("14px");
        s.setHeight("14px");
        s.setBorderRadius("50%");
        s.setBackgroundColor("var(--lumo-contrast-30pct)");
        s.setTransition("background-color 0.5s ease, box-shadow 0.5s ease");
        // Pin to the right edge of the navbar, vertically centered.
        s.setPosition(Style.Position.ABSOLUTE);
        s.setRight("1em");
        s.setTop("0");
        s.setBottom("0");
        s.setMarginTop("auto");
        s.setMarginBottom("auto");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        refresherRegistration = uiRefresher.subscribe(attachEvent.getUI(), events -> beat());
        installClientLogic();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (refresherRegistration != null) {
            refresherRegistration.remove();
            refresherRegistration = null;
        }
        getElement().executeJs("if (this._statusTimer) clearInterval(this._statusTimer);");
        super.onDetach(detachEvent);
    }

    private void beat() {
        getElement().executeJs("this._beat && this._beat();");
    }

    private void installClientLogic() {
        getElement().executeJs(
                """
                const el = this;
                const STALE_MS = 12000;
                const DOWN_MS = 30000;
                const GREEN = '#2ecc71', ORANGE = '#e67e22', RED = '#e74c3c';
                el._lastBeat = Date.now();

                el._update = () => {
                    const age = Date.now() - el._lastBeat;
                    let color = GREEN;
                    if (age > DOWN_MS) color = RED;
                    else if (age > STALE_MS) color = ORANGE;

                    const cs = window.Vaadin && window.Vaadin.connectionState;
                    if (cs) {
                        if (cs.offline || cs.state === 'connection-lost') color = RED;
                        else if (cs.state === 'reconnecting' && color === GREEN) color = ORANGE;
                    }

                    el.style.backgroundColor = color;
                    el.style.boxShadow = '0 0 6px ' + color;
                    el.title = 'Server feed: ' + (color === GREEN ? 'live'
                        : color === ORANGE ? 'no data for ' + Math.round(age / 1000) + 's'
                        : 'down');
                };

                el._beat = () => {
                    el._lastBeat = Date.now();
                    el._update();
                    if (el.animate) {
                        // Quick swell, then a slow multi-second settle back to size.
                        el.animate(
                            [
                                { transform: 'scale(1)', offset: 0 },
                                { transform: 'scale(1.6)', offset: 0.06, easing: 'ease-out' },
                                { transform: 'scale(1)', offset: 1, easing: 'ease-out' }
                            ],
                            { duration: 4000 });
                    }
                };

                if (!el._statusTimer) {
                    el._statusTimer = setInterval(el._update, 1000);
                }
                const cs = window.Vaadin && window.Vaadin.connectionState;
                if (cs && cs.addStateChangeListener && !el._csBound) {
                    el._csBound = true;
                    cs.addStateChangeListener(() => el._update());
                }
                el._update();
                """);
    }
}
