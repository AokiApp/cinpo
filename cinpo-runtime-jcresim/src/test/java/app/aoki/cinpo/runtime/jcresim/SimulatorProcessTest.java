package app.aoki.cinpo.runtime.jcresim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.util.Util;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

final class SimulatorProcessTest {

    // The fixture is a /bin/sh script that stays alive; there is no equally simple
    // long-running batch equivalent, so the teardown path is covered on POSIX only.
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void startupTimeoutStopsOwnedProcess() throws Exception {
        Path runtimeDir = Files.createTempDirectory("cinpo-jcsl-timeout");
        Path pidFile = runtimeDir.resolve("jcsl.pid");
        Path executable = runtimeDir.resolve("jcsl");
        Files.writeString(executable, "#!/bin/sh\necho $$ > '" + pidFile + "'\nexec sleep 30\n");
        assertTrue(executable.toFile().setExecutable(true));

        SimulatorProcess simulatorProcess = new SimulatorProcess(executable, 250L);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                simulatorProcess::ensureReady);
        assertTrue(failure.getMessage().contains("did not become ready"));

        long pid = Long.parseLong(Files.readString(pidFile).trim());
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                "timed-out simulator process should be stopped");
        assertThrows(IllegalStateException.class, simulatorProcess::getPort,
                "failed startup should reset allocated process state");
    }

    @Test
    void processBuilderPrependsSimulatorDirectoryToNativeLibraryPath() throws Exception {
        Path runtimeDir = Files.createTempDirectory("cinpo-jcsl-runtime");
        Path executable = runtimeDir.resolve(Util.simulatorExecutableName());
        Files.writeString(executable, "#!/bin/sh\nexit 0\n");

        boolean windows = Util.isWindows();
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
