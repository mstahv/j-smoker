package in.virit.meater.cloud;

import java.time.Instant;

public record MeaterDevice(
        String id,
        double internalTemperature,
        double ambientTemperature,
        MeaterCook cook,
        Instant updatedAt
) {
    public boolean isCooking() {
        return cook != null;
    }
}
