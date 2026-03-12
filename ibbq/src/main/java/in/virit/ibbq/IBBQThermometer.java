package in.virit.ibbq;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connects to an iBBQ BLE thermometer and streams temperature data.
 * <p>
 * Two usage patterns (similar to Mcp9600):
 * <ul>
 *   <li>{@link #scan()} / {@link #connect(String)} factory methods — this class manages the BLE stack</li>
 *   <li>{@code new IBBQThermometer(device)} — caller manages the DeviceManager lifecycle</li>
 * </ul>
 */
public class IBBQThermometer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IBBQThermometer.class.getName());

    private final BluetoothDevice device;
    private final DeviceManager deviceManager;
    private final boolean ownsDeviceManager;
    private final List<IBBQListener> listeners = new CopyOnWriteArrayList<>();

    private BluetoothGattCharacteristic settingsResponseChar;
    private BluetoothGattCharacteristic credentialsChar;
    private BluetoothGattCharacteristic realtimeChar;
    private BluetoothGattCharacteristic settingsChar;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private AbstractPropertiesChangedHandler propertyHandler;

    /**
     * Wraps an already-connected BluetoothDevice. Caller manages the DeviceManager.
     */
    public IBBQThermometer(BluetoothDevice device) {
        this.device = device;
        this.deviceManager = null;
        this.ownsDeviceManager = false;
    }

    private IBBQThermometer(BluetoothDevice device, DeviceManager deviceManager) {
        this.device = device;
        this.deviceManager = deviceManager;
        this.ownsDeviceManager = true;
    }

    /**
     * Scans for an iBBQ device and connects to the first one found.
     *
     * @param scanTimeoutSeconds how long to scan before giving up
     * @return a connected and authenticated IBBQThermometer
     * @throws IBBQException if no device is found or connection fails
     */
    public static IBBQThermometer scan(int scanTimeoutSeconds) throws IBBQException {
        try {
            DeviceManager dm = getOrCreateDeviceManager();
            dm.setScanFilter(Map.of(
                    DiscoveryFilter.Transport, DiscoveryTransport.LE
            ));

            dm.scanForBluetoothDevices(scanTimeoutSeconds * 1000);

            // Stop discovery before connecting — BlueZ can't scan and connect simultaneously
            BluetoothAdapter adapter = dm.getAdapter();
            if (adapter != null && Boolean.TRUE.equals(adapter.isDiscovering())) {
                adapter.stopDiscovery();
            }
            Thread.sleep(500); // let adapter settle

            List<BluetoothDevice> devices = dm.getDevices();
            for (BluetoothDevice dev : devices) {
                String name = dev.getName();
                if (name != null && (name.contains("iBBQ") || name.contains("xBBQ"))) {
                    LOG.info("Found iBBQ device: " + name + " at " + dev.getAddress());
                    IBBQThermometer thermo = new IBBQThermometer(dev, dm);
                    thermo.connectAndAuthenticate();
                    return thermo;
                }
            }
            throw new IBBQException("No iBBQ device found during scan");
        } catch (IBBQException e) {
            throw e;
        } catch (Exception e) {
            throw new IBBQException("Scan failed", e);
        }
    }

    private static DeviceManager getOrCreateDeviceManager() throws Exception {
        try {
            return DeviceManager.getInstance();
        } catch (IllegalStateException e) {
            // Not yet created
            return DeviceManager.createInstance(false);
        }
    }

    /**
     * Connects directly to an iBBQ device by its BLE MAC address.
     *
     * @param address BLE MAC address (e.g., "AA:BB:CC:DD:EE:FF")
     * @return a connected and authenticated IBBQThermometer
     * @throws IBBQException if connection fails
     */
    public static IBBQThermometer connect(String address) throws IBBQException {
        try {
            DeviceManager dm = getOrCreateDeviceManager();
            dm.setScanFilter(Map.of(
                    DiscoveryFilter.Transport, DiscoveryTransport.LE
            ));

            // Brief scan to discover the device
            dm.scanForBluetoothDevices(5_000);

            List<BluetoothDevice> devices = dm.getDevices();
            for (BluetoothDevice dev : devices) {
                if (address.equalsIgnoreCase(dev.getAddress())) {
                    IBBQThermometer thermo = new IBBQThermometer(dev, dm);
                    thermo.connectAndAuthenticate();
                    return thermo;
                }
            }
            throw new IBBQException("Device not found at address: " + address);
        } catch (IBBQException e) {
            throw e;
        } catch (Exception e) {
            throw new IBBQException("Connect failed", e);
        }
    }

    /**
     * Performs the full iBBQ handshake: connect, discover services, authenticate,
     * set Celsius units, subscribe to notifications, and enable realtime data.
     */
    private static final int CONNECT_RETRIES = 5;
    private static final long CONNECT_RETRY_DELAY_MS = 2000;

    public void connectAndAuthenticate() throws IBBQException {
        try {
            setState(ConnectionState.CONNECTING);

            connectWithRetry();

            // Wait for services to be resolved
            waitForServicesResolved();

            resolveCharacteristics();

            setState(ConnectionState.AUTHENTICATING);

            // Step 1: Subscribe to settings response notifications (FFF1)
            settingsResponseChar.startNotify();
            Thread.sleep(200);

            // Step 2: Write credentials (FFF2)
            credentialsChar.writeValue(IBBQProtocol.CREDENTIALS, Map.of());
            Thread.sleep(200);

            // Step 3: Set Celsius units (FFF5)
            settingsChar.writeValue(IBBQProtocol.UNITS_CELSIUS, Map.of());
            Thread.sleep(200);

            // Step 4: Subscribe to realtime temperature notifications (FFF4)
            realtimeChar.startNotify();
            Thread.sleep(200);

            // Step 5: Register property change handler for notifications
            registerNotificationHandler();

            // Step 6: Enable realtime data (FFF5)
            settingsChar.writeValue(IBBQProtocol.REALTIME_ENABLE, Map.of());

            setState(ConnectionState.READY);

        } catch (IBBQException e) {
            disconnectQuietly();
            setState(ConnectionState.DISCONNECTED);
            throw e;
        } catch (Exception e) {
            disconnectQuietly();
            setState(ConnectionState.DISCONNECTED);
            LOG.log(Level.WARNING, "connectAndAuthenticate failed at " + state + " stage", e);
            throw new IBBQException("Authentication failed at " + state + " stage: " + e.getMessage(), e);
        }
    }

    /**
     * Requests a battery level update. The result arrives asynchronously
     * via {@link IBBQListener#onBatteryLevel(BatteryLevel)}.
     */
    public void requestBatteryLevel() throws IBBQException {
        if (state != ConnectionState.READY) {
            throw new IBBQException("Not connected");
        }
        try {
            settingsChar.writeValue(IBBQProtocol.BATTERY_REQUEST, Map.of());
        } catch (Exception e) {
            throw new IBBQException("Failed to request battery level", e);
        }
    }

    public void addListener(IBBQListener listener) {
        listeners.add(listener);
    }

    public void removeListener(IBBQListener listener) {
        listeners.remove(listener);
    }

    public ConnectionState getState() {
        return state;
    }

    public String getAddress() {
        return device.getAddress();
    }

    public String getName() {
        return device.getName();
    }

    @Override
    public void close() {
        try {
            if (propertyHandler != null && deviceManager != null) {
                try {
                    deviceManager.unRegisterPropertyHandler(propertyHandler);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Error unregistering property handler", e);
                }
            }
            if (realtimeChar != null && Boolean.TRUE.equals(realtimeChar.isNotifying())) {
                realtimeChar.stopNotify();
            }
            if (settingsResponseChar != null && Boolean.TRUE.equals(settingsResponseChar.isNotifying())) {
                settingsResponseChar.stopNotify();
            }
            device.disconnect();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error during disconnect", e);
        } finally {
            setState(ConnectionState.DISCONNECTED);
            if (ownsDeviceManager && deviceManager != null) {
                deviceManager.closeConnection();
            }
        }
    }

    private void connectWithRetry() throws IBBQException, InterruptedException {
        for (int attempt = 1; attempt <= CONNECT_RETRIES; attempt++) {
            try {
                disconnectQuietly();
                Thread.sleep(attempt == 1 ? 500 : CONNECT_RETRY_DELAY_MS);
                if (device.connect()) {
                    LOG.info("BLE connect succeeded on attempt " + attempt);
                    return;
                }
            } catch (Exception e) {
                LOG.info("BLE connect attempt %d/%d failed: %s".formatted(
                        attempt, CONNECT_RETRIES, e.getMessage()));
                if (attempt == CONNECT_RETRIES) {
                    throw new IBBQException("Failed to connect after " + CONNECT_RETRIES + " attempts: " + e.getMessage(), e);
                }
            }
        }
        throw new IBBQException("Failed to connect to device (returned false)");
    }

    private void disconnectQuietly() {
        try {
            device.disconnect();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error during cleanup disconnect", e);
        }
    }

    private void waitForServicesResolved() throws IBBQException, InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (Boolean.TRUE.equals(device.isServicesResolved())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IBBQException("Timeout waiting for service discovery");
    }

    private void resolveCharacteristics() throws IBBQException {
        BluetoothGattService service = device.getGattServiceByUuid(
                IBBQProtocol.SERVICE_UUID.toString());
        if (service == null) {
            throw new IBBQException("iBBQ service (FFF0) not found on device");
        }

        settingsResponseChar = findCharacteristic(service, IBBQProtocol.SETTINGS_RESPONSE_UUID.toString());
        credentialsChar = findCharacteristic(service, IBBQProtocol.CREDENTIALS_UUID.toString());
        realtimeChar = findCharacteristic(service, IBBQProtocol.REALTIME_UUID.toString());
        settingsChar = findCharacteristic(service, IBBQProtocol.SETTINGS_UUID.toString());
    }

    private BluetoothGattCharacteristic findCharacteristic(BluetoothGattService service, String uuid) throws IBBQException {
        BluetoothGattCharacteristic c = service.getGattCharacteristicByUuid(uuid);
        if (c == null) {
            throw new IBBQException("Characteristic " + uuid + " not found");
        }
        return c;
    }

    private void registerNotificationHandler() throws Exception {
        String realtimePath = realtimeChar.getDbusPath();
        String settingsResponsePath = settingsResponseChar.getDbusPath();

        propertyHandler = new AbstractPropertiesChangedHandler() {
            @Override
            public void handle(PropertiesChanged signal) {
                if (signal == null) return;
                Map<String, Variant<?>> changed = signal.getPropertiesChanged();
                Variant<?> valueVariant = changed.get("Value");
                if (valueVariant == null) return;

                byte[] value = toByteArray(valueVariant.getValue());
                if (value == null) return;

                String path = signal.getPath();
                if (path.equals(realtimePath)) {
                    handleTemperatureNotification(value);
                } else if (path.equals(settingsResponsePath)) {
                    handleSettingsNotification(value);
                }
            }
        };

        if (deviceManager != null) {
            deviceManager.registerPropertyHandler(propertyHandler);
        }
    }

    @SuppressWarnings("unchecked")
    private static byte[] toByteArray(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof List<?> list) {
            byte[] result = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Byte b) {
                    result[i] = b;
                } else if (item instanceof Number n) {
                    result[i] = n.byteValue();
                }
            }
            return result;
        }
        return null;
    }

    private void handleTemperatureNotification(byte[] data) {
        try {
            TemperatureUpdate update = IBBQProtocol.decodeTemperatures(data);
            for (IBBQListener listener : listeners) {
                try {
                    listener.onTemperatureUpdate(update);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Listener error", e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to decode temperature data", e);
        }
    }

    private void handleSettingsNotification(byte[] data) {
        try {
            BatteryLevel battery = IBBQProtocol.decodeBattery(data);
            if (battery != null) {
                for (IBBQListener listener : listeners) {
                    try {
                        listener.onBatteryLevel(battery);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Listener error", e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to decode settings response", e);
        }
    }

    private void setState(ConnectionState newState) {
        this.state = newState;
        for (IBBQListener listener : listeners) {
            try {
                listener.onConnectionStateChanged(newState);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener error", e);
            }
        }
    }
}
