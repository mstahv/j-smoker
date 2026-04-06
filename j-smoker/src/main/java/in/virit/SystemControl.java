package in.virit;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Required Pi setup — Create /etc/sudoers.d/j-smoker on the Raspberry Pi:
 *
 *   smoker ALL=(ALL) NOPASSWD: /sbin/shutdown, /sbin/reboot
 *
 *   Then chmod 440 /etc/sudoers.d/j-smoker. Without this, the sudo commands will fail with a password prompt.
 */
@ApplicationScoped
public class SystemControl {

    private static final Logger LOG = Logger.getLogger(SystemControl.class.getName());

    public void shutdown() {
        LOG.info("System shutdown requested from web UI");
        executeCommand("sudo", "shutdown", "-h", "now");
    }

    public void reboot() {
        LOG.info("System reboot requested from web UI");
        executeCommand("sudo", "reboot");
    }

    private void executeCommand(String... command) {
        try {
            new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to execute system command", e);
            throw new RuntimeException("Failed to execute system command: " + e.getMessage(), e);
        }
    }
}
