package app.aoki.cinpo.gradle.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal main class for the {@link CinpoRunTask} functional test. It records the process working
 * directory so the test can assert what the task passed to the forked JVM.
 */
public final class CinpoRunTaskTestMain {

  private CinpoRunTaskTestMain() {}

  public static void main(String[] args) throws IOException {
    Files.writeString(Path.of(args[0]), System.getProperty("user.dir"), StandardCharsets.UTF_8);
  }
}
