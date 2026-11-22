package app.aoki.cinpo.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the classpath-entry predicate used when flattening the appliance. */
class CinpoGradleSupportTest {

  @TempDir Path tempDirectory;

  @Test
  void acceptsDirectoriesAndArchivesRegardlessOfCase() throws IOException {
    Path directory = Files.createDirectories(tempDirectory.resolve("extra-classes"));

    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(directory.toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("lib.jar").toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("LIB.JAR").toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("lib.zip").toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("LIB.ZIP").toFile()));
  }

  @Test
  void rejectsNonArchivesAndMissingFiles() throws IOException {
    assertFalse(CinpoGradleSupport.isBundleableClasspathEntry(write("readme.txt").toFile()));
    assertFalse(
        CinpoGradleSupport.isBundleableClasspathEntry(
            tempDirectory.resolve("missing.jar").toFile()));
  }

  private Path write(String name) throws IOException {
    Path file = tempDirectory.resolve(name);
    Files.writeString(file, name);
    return file;
  }
}
