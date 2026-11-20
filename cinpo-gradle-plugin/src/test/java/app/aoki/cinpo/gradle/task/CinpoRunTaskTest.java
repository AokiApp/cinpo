package app.aoki.cinpo.gradle.task;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the spawned CLI process uses the directory configured on the task.
 */
class CinpoRunTaskTest {

    @TempDir
    Path tempDirectory;

    @Test
    void runsTheProcessInTheConfiguredWorkingDirectory() throws Exception {
        Path projectDirectory = Files.createDirectories(tempDirectory.resolve("project"));
        Path workingDirectory = Files.createDirectories(tempDirectory.resolve("working"));
        Path recorded = tempDirectory.resolve("user-dir.txt");

        Project project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
        CinpoRunTask task = project.getTasks().create("cinpoRun", CinpoRunTask.class);
        task.getWorkingDirectory().set(workingDirectory.toFile());
        task.getMainClass().set(WorkingDirectoryRecorder.class.getName());
        task.getArgs().set(List.of(recorded.toString()));
        task.getClasspath().from((Object[]) System.getProperty("java.class.path").split(File.pathSeparator));

        task.runTask();

        assertEquals(workingDirectory.toRealPath(),
                Path.of(Files.readString(recorded)).toRealPath());
    }
}
