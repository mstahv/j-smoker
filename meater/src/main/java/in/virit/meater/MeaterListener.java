package in.virit.meater;

public interface MeaterListener {

    default void onTemperatureUpdate(TemperatureUpdate update) {}

    default void onBatteryLevel(BatteryLevel battery) {}

    default void onConnectionStateChanged(ConnectionState state) {}
}
