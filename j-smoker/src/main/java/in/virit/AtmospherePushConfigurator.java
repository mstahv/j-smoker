package in.virit;

import com.vaadin.flow.server.VaadinServlet;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import jakarta.servlet.ServletContext;

import java.util.logging.Logger;

/**
 * Caps the Atmosphere websocket write timeout on the Vaadin servlet.
 * <p>
 * Vaadin/Atmosphere performs push websocket writes on the (shared) Vert.x
 * event-loop thread, blocking on a semaphore until the previous send completes.
 * For a slow or dead client that never drains its TCP buffer the write blocks for
 * up to {@code org.atmosphere.websocket.writeTimeout}, which Atmosphere defaults
 * to 60s and Vaadin does not override — long enough to freeze the event loop and
 * thus every client's UI. We lower it so a stuck write fails fast (IOException →
 * the socket is closed) and the event loop recovers in seconds. See
 * blocked-thread.md.
 * <p>
 * Atmosphere reads this only from the servlet's {@code ServletConfig} init
 * parameters (not system properties or application.properties), so an Undertow
 * {@link ServletExtension} — registered via
 * {@code META-INF/services/io.undertow.servlet.ServletExtension} — is the way to
 * set it in Quarkus.
 */
public class AtmospherePushConfigurator implements ServletExtension {

    private static final Logger LOG = Logger.getLogger(AtmospherePushConfigurator.class.getName());

    // Tunable: short enough that a dead client cannot stall the event loop for
    // long, but tolerant of normal network jitter.
    private static final String WEBSOCKET_WRITE_TIMEOUT_MS = "5000";

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
        deploymentInfo.getServlets().values().stream()
                .filter(servlet -> servlet.getServletClass() != null
                        && VaadinServlet.class.isAssignableFrom(servlet.getServletClass()))
                .forEach(servlet -> {
                    servlet.addInitParam("org.atmosphere.websocket.writeTimeout", WEBSOCKET_WRITE_TIMEOUT_MS);
                    LOG.info("Set Atmosphere websocket writeTimeout=%sms on servlet %s".formatted(
                            WEBSOCKET_WRITE_TIMEOUT_MS, servlet.getName()));
                });
    }
}
