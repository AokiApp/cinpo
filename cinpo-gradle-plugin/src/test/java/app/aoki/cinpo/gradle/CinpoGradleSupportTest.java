package app.aoki.cinpo.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the classpath-entry predicate used when flattening the appliance. */
class CinpoGradleSupportTest {

  @TempDir Path tempDirectory;

  /**
   * Offers a directory and JAR/ZIP files in mixed case to {@link
   * CinpoGradleSupport#isBundleableClasspathEntry(java.io.File)} and asserts that each is accepted.
   *
   * <p>Confirms that the predicate treats directories as bundleable and recognizes archive suffixes
   * without regard to letter case.
   *
   * <p>Benefit: prevents an uppercase archive name or a plain classpath directory from being
   * silently dropped from the appliance, which would ship an incomplete runtime image.
   */
  @Test
  @DisplayName("Directories and JAR/ZIP archives are bundleable regardless of letter case")
  void acceptsArchivesAndDirectories() throws IOException {
    Path directory = Files.createDirectories(tempDirectory.resolve("extra-classes"));

    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(directory.toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("lib.jar").toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("LIB.JAR").toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("lib.zip").toFile()));
    assertTrue(CinpoGradleSupport.isBundleableClasspathEntry(write("LIB.ZIP").toFile()));
  }

  /**
   * Offers a text file and a nonexistent JAR path to {@link
   * CinpoGradleSupport#isBundleableClasspathEntry(java.io.File)} and checks both are rejected.
   *
   * <p>Confirms that the predicate excludes ordinary files and paths with no file behind them.
   *
   * <p>Benefit: prevents a random classpath file or a stale path from being copied into the
   * appliance, where it would bloat the image or shadow a real dependency.
   */
  @Test
  @DisplayName("Non-archive files and missing JAR paths are rejected as bundleable entries")
  void rejectsMissingAndNonArchives() throws IOException {
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
