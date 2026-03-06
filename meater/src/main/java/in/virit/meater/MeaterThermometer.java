package in.virit.meater;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MeaterThermometer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MeaterThermometer.class.getName());
    private static final long DEFAULT_POLL_INTERVAL_MS = 1000;

    private final BluetoothDevice device;
    private final DeviceManager deviceManager;
    private final boolean ownsDeviceManager;
    private final List<MeaterListener> listeners = new CopyOnWriteArrayList<>();

    private BluetoothGattCharacteristic temperatureChar;
    private BluetoothGattCharacteristic batteryChar;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private ScheduledExecutorService poller;
    private ScheduledFuture<?> pollFuture;

    public MeaterThermometer(BluetoothDevice device) {
        this.device = device;
        this.deviceManager = null;
        this.ownsDeviceManager = false;
    }

    private MeaterThermometer(BluetoothDevice device, DeviceManager deviceManager) {
        this.device = device;
        this.deviceManager = deviceManager;
        this.ownsDeviceManager = true;
    }

    /**
     * Scans for a Meater probe and connects to the first one found.
     * Identifies probes by their service UUID (not the base station).
     *
     * @param scanTimeoutSeconds how long to scan before giving up
     * @return a connected MeaterThermometer
     * @throws MeaterException if no device is found or connection fails
     */
    public static MeaterThermometer scan(int scanTimeoutSeconds) throws MeaterException {
        try {
            DeviceManager dm = DeviceManager.createInstance(false);
            dm.setScanFilter(Map.of(
                    DiscoveryFilter.Transport, DiscoveryTransport.LE
            ));

            dm.scanForBluetoothDevices(scanTimeoutSeconds * 1000);

            List<BluetoothDevice> devices = dm.getDevices();

            // Log all found devices for debugging
            LOG.info("Scan found %d devices:".formatted(devices.size()));
            for (BluetoothDevice dev : devices) {
                LOG.info("  name=%s addr=%s uuids=%s".formatted(
                        dev.getName(), dev.getAddress(), Arrays.toString(dev.getUuids())));
            }

            // Look for devices with Meater probe name or service UUID
            for (BluetoothDevice dev : devices) {
                if (isMeaterProbe(dev)) {
                    LOG.info("Found Meater probe: " + dev.getName() + " at " + dev.getAddress());
                    MeaterThermometer thermo = new MeaterThermometer(dev, dm);
                    thermo.connectAndSubscribe();
                    return thermo;
                }
            }

            dm.closeConnection();
            throw new MeaterException("No Meater probe found during scan");
        } catch (MeaterException e) {
            throw e;
        } catch (Exception e) {
            throw new MeaterException("Scan failed", e);
        }
    }

    /**
     * Connects directly to a Meater device by its BLE MAC address.
     *
     * @param address BLE MAC address (e.g., "DC:C5:0C:6E:F8:15")
     * @return a connected MeaterThermometer
     * @throws MeaterException if connection fails
     */
    public static MeaterThermometer connect(String address) throws MeaterException {
        try {
            DeviceManager dm = DeviceManager.createInstance(false);
            dm.setScanFilter(Map.of(
                    DiscoveryFilter.Transport, DiscoveryTransport.LE
            ));

            dm.scanForBluetoothDevices(5_000);

            List<BluetoothDevice> devices = dm.getDevices();
            for (BluetoothDevice dev : devices) {
                if (address.equalsIgnoreCase(dev.getAddress())) {
                    MeaterThermometer thermo = new MeaterThermometer(dev, dm);
                    thermo.connectAndSubscribe();
                    return thermo;
                }
            }
            dm.closeConnection();
            throw new MeaterException("Device not found at address: " + address);
        } catch (MeaterException e) {
            throw e;
        } catch (Exception e) {
            throw new MeaterException("Connect failed", e);
        }
    }

    /**
     * Connects to the Meater probe and starts polling for temperature data.
     */
    public void connectAndSubscribe() throws MeaterException {
        try {
            setState(ConnectionState.CONNECTING);

            if (!device.connect()) {
                throw new MeaterException("Failed to connect to device");
            }

            waitForServicesResolved();

            resolveCharacteristics();

            // Do an initial read to verify the characteristic is readable
            byte[] initial = temperatureChar.readValue(Map.of());
            LOG.info(() -> "Initial temperature read (%d bytes): %s".formatted(
                    initial.length, bytesToHex(initial)));

            // Start polling for temperature data
            startPolling(DEFAULT_POLL_INTERVAL_MS);

            setState(ConnectionState.READY);

        } catch (MeaterException e) {
            setState(ConnectionState.DISCONNECTED);
            throw e;
        } catch (Exception e) {
            setState(ConnectionState.DISCONNECTED);
            throw new MeaterException("Connection failed", e);
        }
    }

    /**
     * Reads the battery level.
     */
    public void readBatteryLevel() throws MeaterException {
        if (state != ConnectionState.READY) {
            throw new MeaterException("Not connected");
        }
        try {
            byte[] data = batteryChar.readValue(Map.of());
            LOG.info(() -> "Battery raw data (%d bytes): %s".formatted(
                    data.length, bytesToHex(data)));
            BatteryLevel battery = MeaterProtocol.decodeBattery(data);
            if (battery != null) {
                for (MeaterListener listener : listeners) {
                    try {
                        listener.onBatteryLevel(battery);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Listener error", e);
                    }
                }
            }
        } catch (Exception e) {
            throw new MeaterException("Failed to read battery level", e);
        }
    }

    public void addListener(MeaterListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MeaterListener listener) {
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
            stopPolling();
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

    private void startPolling(long intervalMs) {
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "meater-poll-" + device.getAddress());
            t.setDaemon(true);
            return t;
        });
        pollFuture = poller.scheduleAtFixedRate(this::pollTemperature, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
        }
        if (poller != null) {
            poller.shutdownNow();
        }
    }

    private void pollTemperature() {
        try {
            byte[] data = temperatureChar.readValue(Map.of());
            LOG.fine(() -> "Temperature raw data (%d bytes): %s".formatted(data.length, bytesToHex(data)));
            TemperatureUpdate update = MeaterProtocol.decodeTemperatures(data);
            for (MeaterListener listener : listeners) {
                try {
                    listener.onTemperatureUpdate(update);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Listener error", e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to read temperature data", e);
        }
    }

    private static boolean isMeaterProbe(BluetoothDevice dev) {
        // Check by advertised name
        String name = dev.getName();
        if (name != null && name.equals("MEATER")) {
            return true;
        }
        // Check by advertised service UUID
        String[] uuids = dev.getUuids();
        if (uuids != null) {
            List<String> uuidList = Arrays.asList(uuids);
            return MeaterProtocol.isMeaterProbe(uuidList);
        }
        return false;
    }

    private void waitForServicesResolved() throws MeaterException, InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (Boolean.TRUE.equals(device.isServicesResolved())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new MeaterException("Timeout waiting for service discovery");
    }

    private void resolveCharacteristics() throws MeaterException {
        // Try each known probe service UUID
        List<BluetoothGattService> services = device.getGattServices();
        List<String> serviceUuids = new ArrayList<>();
        for (BluetoothGattService s : services) {
            if (s.getUuid() != null) {
                serviceUuids.add(s.getUuid());
            }
        }

        LOG.info("Device services: " + serviceUuids);

        String probeServiceUuid = MeaterProtocol.findProbeServiceUuid(serviceUuids);
        if (probeServiceUuid == null) {
            throw new MeaterException(
                    "Not a Meater probe — no known service UUID found. Services: " + serviceUuids);
        }

        BluetoothGattService service = device.getGattServiceByUuid(probeServiceUuid);

        temperatureChar = findCharacteristic(service, MeaterProtocol.TEMPERATURE_UUID.toString());
        batteryChar = findCharacteristic(service, MeaterProtocol.BATTERY_UUID.toString());
    }

    private BluetoothGattCharacteristic findCharacteristic(BluetoothGattService service, String uuid)
            throws MeaterException {
        BluetoothGattCharacteristic c = service.getGattCharacteristicByUuid(uuid);
        if (c == null) {
            throw new MeaterException("Characteristic " + uuid + " not found");
        }
        return c;
    }

    private void setState(ConnectionState newState) {
        this.state = newState;
        for (MeaterListener listener : listeners) {
            try {
                listener.onConnectionStateChanged(newState);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener error", e);
            }
        }
    }

    static String bytesToHex(byte[] data) {
        if (data == null) return "null";
        var sb = new StringBuilder();
        for (byte b : data) {
            sb.append("%02x ".formatted(b & 0xFF));
        }
        return sb.toString().trim();
    }
}
