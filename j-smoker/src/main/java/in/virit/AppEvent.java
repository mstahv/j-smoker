package in.virit;

/**
 * Marker interface for application-wide events that should be
 * broadcast to all connected UIs via {@link UiRefresher}.
 * Implement as records for simple value events.
 */
public interface AppEvent {

    record FoodTargetsChanged() implements AppEvent {}

    record SetpointChanged(double setpoint) implements AppEvent {}

    record AutoControlStateChanged(boolean active) implements AppEvent {}
}
