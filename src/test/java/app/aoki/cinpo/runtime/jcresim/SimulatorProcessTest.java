package app.aoki.cinpo.runtime.jcresim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SimulatorProcessTest {

    @Test
    void processBuilderPrependsSimulatorDirectoryToLdLibraryPath() throws Exception {
        Path runtimeDir = Files.createTempDirectory("cinpo-jcsl-runtime");
        Path executable = runtimeDir.resolve("jcsl");
        Files.writeString(executable, "#!/bin/sh\nexit 0\n");

        SimulatorProcess simulatorProcess = new SimulatorProcess(executable);
        ProcessBuilder builder = simulatorProcess.createSimulatorProcessBuilder(12345);
        Map<String, String> environment = builder.environment();
        String ldLibraryPath = environment.get("LD_LIBRARY_PATH");

        assertTrue(ldLibraryPath != null && !ldLibraryPath.isBlank(), "LD_LIBRARY_PATH should be configured");
        assertEquals(runtimeDir.toAbsolutePath().toString(), ldLibraryPath.split(":", 2)[0]);
        assertEquals(runtimeDir.toFile(), builder.directory());
    }
}
