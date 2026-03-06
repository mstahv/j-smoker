package in.virit.meater.cloud;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
class MeaterCloudLiveTest {

    /**
     * Live test against Meater Cloud API.
     * Set system properties: -Dmeater.email=... -Dmeater.password=...
     * Requires: Meater app open and connected to probe(s).
     */
    @Test
    void loginAndListDevices() throws Exception {
        String email = System.getProperty("meater.email");
        String password = System.getProperty("meater.password");
        if (email == null || password == null) {
            System.out.println("Skipping: set -Dmeater.email and -Dmeater.password to run");
            return;
        }

        try (var client = new MeaterCloudClient()) {
            client.login(email, password);
            assertTrue(client.isAuthenticated());

            List<MeaterDevice> devices = client.getDevices();
            System.out.println("Found " + devices.size() + " device(s):");
            for (MeaterDevice dev : devices) {
                System.out.printf("  ID=%s internal=%.1f°C ambient=%.1f°C updated=%s cooking=%s%n",
                        dev.id(),
                        dev.internalTemperature(),
                        dev.ambientTemperature(),
                        dev.updatedAt(),
                        dev.isCooking());
                if (dev.isCooking()) {
                    MeaterCook cook = dev.cook();
                    System.out.printf("    Cook: name=%s state=%s target=%.1f°C peak=%.1f°C elapsed=%ds remaining=%ds%n",
                            cook.name(), cook.state(),
                            cook.targetTemperature(), cook.peakTemperature(),
                            cook.timeElapsed(), cook.timeRemaining());
                }
            }
        }
    }

    /**
     * Test polling for 90 seconds (3 updates at 30s interval).
     */
    @Test
    void pollDevices() throws Exception {
        String email = System.getProperty("meater.email");
        String password = System.getProperty("meater.password");
        if (email == null || password == null) {
            System.out.println("Skipping: set -Dmeater.email and -Dmeater.password to run");
            return;
        }

        var updatesReceived = new CountDownLatch(3);

        try (var client = new MeaterCloudClient()) {
            client.login(email, password);

            client.addListener(new MeaterCloudListener() {
                @Override
                public void onDevicesUpdated(List<MeaterDevice> devices) {
                    System.out.println("Poll update — " + devices.size() + " device(s):");
                    for (MeaterDevice dev : devices) {
                        System.out.printf("  %s: internal=%.1f°C ambient=%.1f°C%n",
                                dev.id(), dev.internalTemperature(), dev.ambientTemperature());
                    }
                    updatesReceived.countDown();
                }

                @Override
                public void onError(Exception error) {
                    System.out.println("Poll error: " + error.getMessage());
                }
            });

            client.startPolling(30);
            assertTrue(updatesReceived.await(100, TimeUnit.SECONDS),
                    "Should receive 3 poll updates within 100 seconds");
        }
    }
}
