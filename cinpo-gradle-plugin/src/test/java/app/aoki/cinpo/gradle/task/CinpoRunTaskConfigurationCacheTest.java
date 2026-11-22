package app.aoki.cinpo.gradle.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@link CinpoRunTask} through Gradle TestKit and verifies that the task neither reads {@code
 * Task.project} at execution time nor prevents the configuration cache from being reused.
 */
class CinpoRunTaskConfigurationCacheTest {

  @TempDir Path tempDirectory;

  @Test
  void runsWithConfigurationCache() throws Exception {
    Path projectDirectory = Files.createDirectories(tempDirectory.resolve("project"));
    Path observedWorkingDirectory = tempDirectory.resolve("observed-working-directory.txt");

    write(
        projectDirectory.resolve("settings.gradle"),
        "rootProject.name = 'configuration-cache-test'\n");
    write(projectDirectory.resolve("build.gradle"), buildScript(observedWorkingDirectory));

    BuildResult first = runner(projectDirectory).build();
    assertTrue(first.getOutput().contains("Configuration cache entry stored."), first.getOutput());
    assertEquals(projectDirectory.toRealPath().toString(), read(observedWorkingDirectory));

    BuildResult second = runner(projectDirectory).build();
    assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
    assertEquals(projectDirectory.toRealPath().toString(), read(observedWorkingDirectory));
  }

  private GradleRunner runner(Path projectDirectory) {
    return GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withPluginClasspath()
        .withArguments(
            "probeWorkingDirectory", "--configuration-cache", "--offline", "--stacktrace");
  }

  private String buildScript(Path observedWorkingDirectory) throws Exception {
    Path testClasses =
        Path.of(
            CinpoRunTaskConfigurationCacheTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    return """
    plugins {
        id 'app.aoki.cinpo'
    }

    dependencies {
        // The plugin only reads the declared version; this task never
        // resolves the dependency.
        implementation 'app.aoki.cinpo:cinpo:0.0.0'
    }

    tasks.register('probeWorkingDirectory', app.aoki.cinpo.gradle.task.CinpoRunTask) {
        classpath = files(%s)
        mainClass = 'app.aoki.cinpo.gradle.task.CinpoRunTaskTestMain'
        args = [%s]
        workingDirectory = layout.projectDirectory
    }
    """
        .formatted(
            groovyString(testClasses.toString()),
            groovyString(observedWorkingDirectory.toString()));
  }

  private static String groovyString(String value) {
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }

  private static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }
}
