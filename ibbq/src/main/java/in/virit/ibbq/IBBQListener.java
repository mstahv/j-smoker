package in.virit.ibbq;

/**
 * Callback interface for iBBQ thermometer events.
 * All methods have default no-op implementations so consumers
 * only need to override the events they care about.
 */
public interface IBBQListener {

    default void onTemperatureUpdate(TemperatureUpdate update) {}

    default void onBatteryLevel(BatteryLevel battery) {}

    default void onConnectionStateChanged(ConnectionState state) {}
}
