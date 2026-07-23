package app.aoki.cinpo.runtime.jcresim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
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
    private final long startupTimeoutMillis;
    private Process process;
    private boolean ownsProcess;
    private Integer port;
    private final ByteArrayOutputStream stderrCapture = new ByteArrayOutputStream();
    private Thread stderrDrainer;

    /**
     * Create a simulator process manager.
     *
     * @param executable path to the jcsl executable
     */
    SimulatorProcess(Path executable) {
        this(executable, STARTUP_TIMEOUT_MILLIS);
    }

    SimulatorProcess(Path executable, long startupTimeoutMillis) {
        this.executable = Objects.requireNonNull(executable);
        if (startupTimeoutMillis <= 0) {
            throw new IllegalArgumentException("startupTimeoutMillis must be positive");
        }
        this.startupTimeoutMillis = startupTimeoutMillis;
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

        try {
            waitForSimulatorReady();
        } catch (RuntimeException | Error startupFailure) {
            stopOwnedSimulator();
            throw startupFailure;
        }
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
        ProcessBuilder processBuilder = createSimulatorProcessBuilder(port);

        try {
            Process proc = processBuilder.start();
            stderrCapture.reset();
            stderrDrainer = drainAsync(proc.getInputStream(), stderrCapture);
            return proc;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start Oracle JCRE simulator using " + executable, e);
        }
    }

    ProcessBuilder createSimulatorProcessBuilder(int simulatorPort) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                executable.toAbsolutePath().toString(),
                "-p=" + simulatorPort);
        Path executableParent = executable.toAbsolutePath().getParent();
        if (executableParent != null) {
            processBuilder.directory(executableParent.toFile());
            prependLdLibraryPath(processBuilder.environment(), executableParent);
        }
        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
        // Merge stderr → stdout so we capture everything jcsl emits
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        return processBuilder;
    }

    private static void prependLdLibraryPath(Map<String, String> environment, Path libraryDirectory) {
        String libPath = libraryDirectory.toAbsolutePath().toString();
        String existing = environment.get("LD_LIBRARY_PATH");
        if (existing != null && !existing.isBlank()) {
            environment.put("LD_LIBRARY_PATH", libPath + ":" + existing);
        } else {
            environment.put("LD_LIBRARY_PATH", libPath);
        }
    }

    private void waitForSimulatorReady() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupTimeoutMillis);
        while (System.nanoTime() < deadline) {
            if (isSimulatorReachable()) {
                return;
            }
            if (process != null && !process.isAlive()) {
                awaitStderrDrainer();
                int exitCode = process.exitValue();
                String stderr = stderrCapture.toString(StandardCharsets.UTF_8).strip();
                String diagnostic = "Oracle JCRE simulator terminated with exit code " + exitCode
                        + " before becoming ready.";
                if (!stderr.isEmpty()) {
                    diagnostic += "\nstderr:\n" + stderr;
                }
                throw new IllegalStateException(diagnostic);
            }
            sleepQuietly(100L);
        }

        String stderr = stderrCapture.toString(StandardCharsets.UTF_8).strip();
        String msg = "Oracle JCRE simulator did not become ready within " + startupTimeoutMillis + " ms.";
        if (!stderr.isEmpty()) {
            msg += "\nstderr (so far):\n" + stderr;
        }
        throw new IllegalStateException(msg);
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
            stderrDrainer = null;
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
            stderrDrainer = null;
        }
    }

    private static int allocateEphemeralPort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate an ephemeral port for Oracle JCRE simulator", e);
        }
    }

    private void awaitStderrDrainer() {
        if (stderrDrainer == null) {
            return;
        }
        try {
            stderrDrainer.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread drainAsync(InputStream source, OutputStream sink) {
        Thread t = new Thread(() -> {
            try {
                source.transferTo(sink);
            } catch (IOException ignored) {
                // Stream closed when process terminates
            }
        }, "jcsl-stderr-drainer");
        t.setDaemon(true);
        t.start();
        return t;
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
