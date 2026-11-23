package app.aoki.cinpo.gradle.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests that the spawned CLI process uses the directory configured on the task. */
class CinpoRunTaskTest {

  @TempDir Path tempDirectory;

  /**
   * Configures {@link CinpoRunTask} with a working directory separate from the project directory,
   * runs it, and reads the user directory recorded by the spawned process.
   *
   * <p>Confirms the behavior asserted by this class: the child process starts in the directory set
   * on the task rather than the Gradle project directory.
   *
   * <p>Benefit: prevents a relative-path regression where the CLI resolves inputs against the wrong
   * directory and fails or writes outputs to an unexpected location.
   */
  @Test
  @DisplayName("The spawned CLI process uses the working directory configured on the task")
  void usesConfiguredWorkingDirectory() throws Exception {
    Path projectDirectory = Files.createDirectories(tempDirectory.resolve("project"));
    Path workingDirectory = Files.createDirectories(tempDirectory.resolve("working"));
    Path recorded = tempDirectory.resolve("user-dir.txt");

    Project project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
    CinpoRunTask task = project.getTasks().create("cinpoRun", CinpoRunTask.class);
    task.getWorkingDirectory().set(workingDirectory.toFile());
    task.getMainClass().set(WorkingDirectoryRecorder.class.getName());
    task.getArgs().set(List.of(recorded.toString()));
    task.getClasspath()
        .from((Object[]) System.getProperty("java.class.path").split(File.pathSeparator));

    task.runTask();

    assertEquals(workingDirectory.toRealPath(), Path.of(Files.readString(recorded)).toRealPath());
  }
}
