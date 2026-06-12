package app.aoki.cinpo.runtime.jcresim;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of an Oracle jcsl simulator process.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Ephemeral port allocation via ServerSocket(0)</li>
 *   <li>Process startup and management</li>
 *   <li>Socket-based readiness detection</li>
 *   <li>Graceful shutdown with destroy() → destroyForcibly() fallback</li>
 * </ul>
 */
final class SimulatorProcess implements AutoCloseable {

    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final long STARTUP_TIMEOUT_MILLIS = 10_000L;
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 2_000L;

    private final Path executable;
    private Process process;
    private boolean ownsProcess;
    private Integer port;

    /**
     * Create a simulator process manager.
     *
     * @param executable path to the jcsl executable
     */
    SimulatorProcess(Path executable) {
        this.executable = Objects.requireNonNull(executable);
    }

    /**
     * Ensure the simulator is ready for connections.
     * <p>
     * This method will:
     * <ol>
     *   <li>Check if the simulator is already reachable (e.g., externally managed)</li>
     *   <li>Allocate an ephemeral port if needed</li>
     *   <li>Start the simulator process if not already running</li>
     *   <li>Wait for the simulator to become ready via socket connection</li>
     * </ol>
     */
    void ensureReady() {
        if (isSimulatorReachable()) {
            ownsProcess = false;
            return;
        }

        if (port == null) {
            port = allocateEphemeralPort();
        }

        if (process == null || !process.isAlive()) {
            process = startSimulatorProcess();
            ownsProcess = true;
        }

        waitForSimulatorReady();
    }

    /**
     * Get the port number the simulator is listening on.
     *
     * @return the simulator port
     * @throws IllegalStateException if the port has not been allocated
     */
    int getPort() {
        if (port == null) {
            throw new IllegalStateException("Simulator port has not been allocated yet");
        }
        return port;
    }

    @Override
    public void close() {
        stopOwnedSimulator();
    }

    private Process startSimulatorProcess() {
        ProcessBuilder processBuilder = new ProcessBuilder(
                executable.toAbsolutePath().toString(),
                "-p=" + port);
        Path executableParent = executable.toAbsolutePath().getParent();
        if (executableParent != null) {
            processBuilder.directory(executableParent.toFile());
        }
        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try {
            return processBuilder.start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start Oracle JCRE simulator using " + executable, e);
        }
    }

    private void waitForSimulatorReady() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STARTUP_TIMEOUT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (isSimulatorReachable()) {
                return;
            }
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException("Oracle JCRE simulator terminated before becoming ready");
            }
            sleepQuietly(100L);
        }

        throw new IllegalStateException(
                "Oracle JCRE simulator did not become ready within " + STARTUP_TIMEOUT_MILLIS + " ms");
    }

    private boolean isSimulatorReachable() {
        if (port == null) {
            return false;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK_HOST, port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void stopOwnedSimulator() {
        if (!ownsProcess || process == null) {
            process = null;
            port = null;
            ownsProcess = false;
            return;
        }

        process.destroy();
        try {
            if (!process.waitFor(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            process = null;
            port = null;
            ownsProcess = false;
        }
    }

    private static int allocateEphemeralPort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate an ephemeral port for Oracle JCRE simulator", e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Oracle JCRE simulator startup", e);
        }
    }
}
