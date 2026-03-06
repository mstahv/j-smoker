package in.virit.meater.cloud;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Package-private DTOs matching the Meater Cloud REST API JSON structure.
 */
class MeaterApiDtos {

    private MeaterApiDtos() {}

    // --- POST /v1/login ---

    record LoginRequest(String email, String password) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(LoginData data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record LoginData(String token) {}
    }

    // --- GET /v1/devices ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DevicesResponse(DevicesData data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record DevicesData(List<DeviceDto> devices) {}
    }

    // --- GET /v1/devices/{id} ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeviceResponse(DeviceDto data) {}

    // --- Shared device structure ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeviceDto(
            String id,
            TemperatureDto temperature,
            CookDto cook,
            @JsonProperty("updated_at") long updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TemperatureDto(
            double internal,
            double ambient
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CookDto(
            String id,
            String name,
            String state,
            CookTemperatureDto temperature,
            CookTimeDto time
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CookTemperatureDto(
            Double target,
            Double peak
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CookTimeDto(
            Integer elapsed,
            Integer remaining
    ) {}
}
