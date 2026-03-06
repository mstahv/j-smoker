package in.virit.meater;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("hardware")
class MeaterHardwareTest {

    /**
     * Scan for a Meater probe, connect, and read temperature data.
     * Requires: base stations unplugged so the probe advertises directly.
     */
    @Test
    void scanConnectAndReadTemperatures() throws Exception {
        var tempReceived = new CountDownLatch(3);
        var lastUpdate = new AtomicReference<TemperatureUpdate>();
        var lastState = new AtomicReference<ConnectionState>();

        try (MeaterThermometer thermo = MeaterThermometer.scan(15)) {
            System.out.println("Connected to: " + thermo.getName() + " at " + thermo.getAddress());

            thermo.addListener(new MeaterListener() {
                @Override
                public void onTemperatureUpdate(TemperatureUpdate update) {
                    lastUpdate.set(update);
                    for (MeaterProbeReading probe : update.probes()) {
                        System.out.printf("Probe %d: tip=%.1f°C ambient=%.1f°C connected=%s%n",
                                probe.channel(),
                                probe.tipCelsius(),
                                probe.ambientCelsius(),
                                probe.isConnected());
                    }
                    tempReceived.countDown();
                }

                @Override
                public void onBatteryLevel(BatteryLevel battery) {
                    System.out.println("Battery: " + battery.percent() + "%");
                }

                @Override
                public void onConnectionStateChanged(ConnectionState state) {
                    lastState.set(state);
                    System.out.println("State: " + state);
                }
            });

            // Read battery
            thermo.readBatteryLevel();

            // Wait for a few temperature readings
            assertTrue(tempReceived.await(10, TimeUnit.SECONDS),
                    "Should receive at least 3 temperature updates within 10 seconds");

            assertEquals(ConnectionState.READY, thermo.getState());

            TemperatureUpdate update = lastUpdate.get();
            assertNotNull(update);
            assertFalse(update.probes().isEmpty());
            assertNotNull(update.timestamp());
        }
    }

    /**
     * Connect directly by address (use after discovering the address via scan test).
     */
    @Test
    void connectByAddress() throws Exception {
        String address = System.getProperty("meater.address");
        if (address == null) {
            System.out.println("Skipping: set -Dmeater.address=XX:XX:XX:XX:XX:XX to run");
            return;
        }

        var tempReceived = new CountDownLatch(1);

        try (MeaterThermometer thermo = MeaterThermometer.connect(address)) {
            thermo.addListener(new MeaterListener() {
                @Override
                public void onTemperatureUpdate(TemperatureUpdate update) {
                    for (MeaterProbeReading probe : update.probes()) {
                        System.out.printf("Probe %d: tip=%.1f°C ambient=%.1f°C%n",
                                probe.channel(), probe.tipCelsius(), probe.ambientCelsius());
                    }
                    tempReceived.countDown();
                }
            });

            assertTrue(tempReceived.await(10, TimeUnit.SECONDS));
        }
    }
}
