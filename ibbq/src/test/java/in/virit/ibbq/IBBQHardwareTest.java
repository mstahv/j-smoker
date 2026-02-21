package in.virit.ibbq;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hardware integration test — requires an iBBQ device powered on and in range.
 * Run with: mvn test -Phardware
 */
@Tag("hardware")
class IBBQHardwareTest {

    @Test
    void scanConnectAndReadTemperatures() throws Exception {
        CountDownLatch tempLatch = new CountDownLatch(3);
        AtomicReference<TemperatureUpdate> lastUpdate = new AtomicReference<>();
        AtomicReference<ConnectionState> lastState = new AtomicReference<>();

        try (IBBQThermometer thermo = IBBQThermometer.scan(15)) {
            thermo.addListener(new IBBQListener() {
                @Override
                public void onTemperatureUpdate(TemperatureUpdate update) {
                    System.out.println("Temperature update: " + update.probes());
                    lastUpdate.set(update);
                    tempLatch.countDown();
                }

                @Override
                public void onConnectionStateChanged(ConnectionState state) {
                    System.out.println("State: " + state);
                    lastState.set(state);
                }

                @Override
                public void onBatteryLevel(BatteryLevel battery) {
                    System.out.println("Battery: " + battery.percent() + "%");
                }
            });

            assertEquals(ConnectionState.READY, thermo.getState());
            assertNotNull(thermo.getAddress());
            assertNotNull(thermo.getName());

            thermo.requestBatteryLevel();

            // Wait for at least 3 temperature updates (they come every ~1s)
            assertTrue(tempLatch.await(30, TimeUnit.SECONDS),
                    "Should receive temperature updates within 30 seconds");

            TemperatureUpdate update = lastUpdate.get();
            assertNotNull(update);
            assertFalse(update.probes().isEmpty(), "Should have at least one probe");
            assertNotNull(update.timestamp());

            // At least the first probe should be connected (ambient)
            ProbeReading firstProbe = update.probes().getFirst();
            assertEquals(0, firstProbe.channel());
        }
    }
}
