package app.aoki.cinpo.runtime.jcdksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

final class SimulatorProcessTest {

  // The fixture is a /bin/sh script that stays alive; there is no equally simple
  // long-running batch equivalent, so the teardown path is covered on POSIX only.
  /**
   * Starts a fixture simulator that never reports ready and waits for the readiness timeout.
   *
   * <p>Verifies that ensureReady throws after the timeout, that the spawned process is no longer
   * alive, and that getPort rejects the failed startup state.
   *
   * <p>Benefit: without this test, a leaked simulator process could keep its PID file or port in
   * use and break the next run.
   */
  @DisabledOnOs(OS.WINDOWS)
  @Test
  @DisplayName("A simulator that never becomes ready is stopped when startup times out")
  void startupTimeoutStopsOwnedProcess() throws Exception {
    Path runtimeDir = Files.createTempDirectory("cinpo-jcsl-timeout");
    Path pidFile = runtimeDir.resolve("jcsl.pid");
    Path executable = runtimeDir.resolve("jcsl");
    Files.writeString(executable, "#!/bin/sh\necho $$ > '" + pidFile + "'\nexec sleep 30\n");
    assertTrue(executable.toFile().setExecutable(true));

    SimulatorProcess simulatorProcess = new SimulatorProcess(executable, 250L);

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, simulatorProcess::ensureReady);
    assertTrue(failure.getMessage().contains("did not become ready"));

    long pid = Long.parseLong(Files.readString(pidFile).trim());
    assertFalse(
        ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
        "timed-out simulator process should be stopped");
    assertThrows(
        IllegalStateException.class,
        simulatorProcess::getPort,
        "failed startup should reset allocated process state");
  }

  /**
   * Creates a simulator process builder and inspects its environment and working directory.
   *
   * <p>Verifies that the runtime directory comes first in PATH on Windows or LD_LIBRARY_PATH
   * elsewhere, and that the builder runs from that directory.
   *
   * <p>Benefit: without this test, a stale system simulator library could load instead of the
   * bundled one and cause native failures that are hard to reproduce.
   */
  @Test
  @DisplayName("The simulator directory is first on the native library path")
  void builderPrependsSimulatorDirectory() throws Exception {
    Path runtimeDir = Files.createTempDirectory("cinpo-jcsl-runtime");
    Path executable = runtimeDir.resolve(JcdkSimPlatform.simulatorExecutableName());
    Files.writeString(executable, "#!/bin/sh\nexit 0\n");

    boolean windows = JcdkSimPlatform.isWindows();
    String variable = windows ? "PATH" : "LD_LIBRARY_PATH";
    String separator = windows ? ";" : ":";

    SimulatorProcess simulatorProcess = new SimulatorProcess(executable);
    ProcessBuilder builder = simulatorProcess.createSimulatorProcessBuilder(12345);
    Map<String, String> environment = builder.environment();
    String libraryPath = environment.get(variable);

    assertTrue(libraryPath != null && !libraryPath.isBlank(), variable + " should be configured");
    assertEquals(runtimeDir.toAbsolutePath().toString(), libraryPath.split(separator, 2)[0]);
    assertEquals(runtimeDir.toFile(), builder.directory());
  }
}
