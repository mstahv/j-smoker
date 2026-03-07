package in.virit.meater.cloud;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MeaterCloudClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MeaterCloudClient.class.getName());
    private static final String BASE_URL = "https://public-api.cloud.meater.com/v1";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<MeaterCloudListener> listeners = new CopyOnWriteArrayList<>();

    private String token;
    private ScheduledExecutorService poller;
    private ScheduledFuture<?> pollFuture;

    public MeaterCloudClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Authenticate with the Meater Cloud API.
     */
    public void login(String email, String password) throws MeaterCloudException {
        try {
            String body = mapper.writeValueAsString(new MeaterApiDtos.LoginRequest(email, password));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponse(response);

            var loginResponse = mapper.readValue(response.body(), MeaterApiDtos.LoginResponse.class);
            this.token = loginResponse.data().token();
            LOG.info("Authenticated with Meater Cloud");

        } catch (MeaterCloudException e) {
            throw e;
        } catch (Exception e) {
            throw new MeaterCloudException("Login failed", e);
        }
    }

    /**
     * Get all devices currently connected to Meater Cloud.
     */
    public List<MeaterDevice> getDevices() throws MeaterCloudException {
        requireAuth();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/devices"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponse(response);

            var devicesResponse = mapper.readValue(response.body(), MeaterApiDtos.DevicesResponse.class);
            return devicesResponse.data().devices().stream()
                    .map(MeaterCloudClient::toDevice)
                    .toList();

        } catch (MeaterCloudException e) {
            throw e;
        } catch (Exception e) {
            throw new MeaterCloudException("Failed to get devices", e);
        }
    }

    /**
     * Get a single device by ID.
     */
    public MeaterDevice getDevice(String id) throws MeaterCloudException {
        requireAuth();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/devices/" + id))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponse(response);

            var deviceResponse = mapper.readValue(response.body(), MeaterApiDtos.DeviceResponse.class);
            return toDevice(deviceResponse.data());

        } catch (MeaterCloudException e) {
            throw e;
        } catch (Exception e) {
            throw new MeaterCloudException("Failed to get device: " + id, e);
        }
    }

    /**
     * Start polling for device updates.
     * The Meater Cloud API recommends polling no more than once per 30 seconds.
     */
    public void startPolling(long intervalSeconds) {
        stopPolling();
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "meater-cloud-poll");
            t.setDaemon(true);
            return t;
        });
        pollFuture = poller.scheduleAtFixedRate(this::poll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    public void addListener(MeaterCloudListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MeaterCloudListener listener) {
        listeners.remove(listener);
    }

    public boolean isAuthenticated() {
        return token != null;
    }

    @Override
    public void close() {
        stopPolling();
        token = null;
    }

    private void poll() {
        try {
            List<MeaterDevice> devices = getDevices();
            for (MeaterCloudListener listener : listeners) {
                try {
                    listener.onDevicesUpdated(devices);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Listener error", e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Poll failed", e);
            for (MeaterCloudListener listener : listeners) {
                try {
                    listener.onError(e);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Listener error", ex);
                }
            }
        }
    }

    private void requireAuth() throws MeaterCloudException {
        if (token == null) {
            throw new MeaterCloudException("Not authenticated — call login() first");
        }
    }

    private void checkResponse(HttpResponse<String> response) throws MeaterCloudException {
        int code = response.statusCode();
        if (code == 401) {
            token = null;
            throw new MeaterCloudException("Authentication failed", 401);
        }
        if (code == 404) {
            throw new MeaterCloudException("Device not found or offline", 404);
        }
        if (code == 429) {
            throw new MeaterCloudException("Rate limited — slow down polling", 429);
        }
        if (code >= 400) {
            throw new MeaterCloudException(
                    "API error %d: %s".formatted(code, response.body()), code);
        }
    }

    private static MeaterDevice toDevice(MeaterApiDtos.DeviceDto dto) {
        MeaterCook cook = dto.cook() != null ? toCook(dto.cook()) : null;
        return new MeaterDevice(
                dto.id(),
                dto.temperature().internal(),
                dto.temperature().ambient(),
                cook,
                Instant.ofEpochSecond(dto.updatedAt())
        );
    }

    private static MeaterCook toCook(MeaterApiDtos.CookDto dto) {
        Integer remaining = dto.time() != null ? dto.time().remaining() : null;
        Integer elapsed = dto.time() != null ? dto.time().elapsed() : null;
        if (remaining != null && remaining < 0) remaining = null;
        Double target = dto.temperature() != null ? dto.temperature().target() : null;
        Double peak = dto.temperature() != null ? dto.temperature().peak() : null;
        return new MeaterCook(
                dto.id(), dto.name(), dto.state(),
                target, peak, elapsed, remaining
        );
    }
}
