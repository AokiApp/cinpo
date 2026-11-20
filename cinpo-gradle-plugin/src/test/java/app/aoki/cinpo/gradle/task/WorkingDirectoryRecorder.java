package app.aoki.cinpo.gradle.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the process working directory to the file named by the first argument.
 *
 * <p>Used by {@link CinpoRunTaskTest} as the main class of the spawned process.
 */
public final class WorkingDirectoryRecorder {
    private WorkingDirectoryRecorder() {
    }

    public static void main(String[] args) throws IOException {
        Files.writeString(Path.of(args[0]), System.getProperty("user.dir"), StandardCharsets.UTF_8);
    }
}
