package app.aoki.cinpo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileLoaderTest {

  @TempDir Path projectDir;

  /**
   * Writes two profiles with the same name, one in the project-local profile directory and one in
   * the legacy project/profile directory, then loads that name.
   *
   * <p>This checks that the project-local directory wins and that the returned profile carries the
   * values from that file.
   *
   * <p>Benefit: the search order is explicit when both locations hold a profile with the same name.
   */
  @DisplayName("A named profile resolves from the project-local profile directory.")
  @Test
  void loadsProfileFromProjectDir() throws IOException {
    writeProfile(projectDir.resolve("profile/card1.yaml"), "card1", "pcsc");
    writeProfile(projectDir.resolve("project/profile/card1.yaml"), "legacy-card1", "jcdksim");

    Profile profile = ProfileLoader.load("card1", projectDir);

    assertEquals("card1", profile.name());
    assertEquals("pcsc", profile.runtime());
  }

  /**
   * Writes a profile only in the legacy project/profile directory and tries to load it by name.
   *
   * <p>This checks that the load fails and that the error message names the current search location
   * but not the legacy directory.
   *
   * <p>Benefit: a user with the old layout gets an error that points to the supported location.
   */
  @DisplayName("A profile found only in the legacy directory is not loaded.")
  @Test
  void ignoresLegacyProfileDirectory() throws IOException {
    writeProfile(projectDir.resolve("project/profile/card1.yaml"), "legacy-card1", "jcdksim");

    IOException exception =
        assertThrows(IOException.class, () -> ProfileLoader.load("card1", projectDir));

    assertTrue(
        exception
            .getMessage()
            .contains(
                projectDir.resolve("profile/card1.yaml").toAbsolutePath().normalize().toString()));
    assertFalse(
        exception
            .getMessage()
            .contains(
                projectDir
                    .resolve("project/profile/card1.yaml")
                    .toAbsolutePath()
                    .normalize()
                    .toString()));
  }

  /**
   * Writes a profile in a subdirectory and loads it by a relative path that includes the file name.
   *
   * <p>This checks that a path-like name is resolved against the project directory instead of being
   * treated as the name of a profile in the profile directory.
   *
   * <p>Benefit: a project can keep profiles outside the standard profile directory.
   */
  @DisplayName("A path-like name resolves relative to the project directory.")
  @Test
  void loadsProfileByRelativePath() throws IOException {
    writeProfile(projectDir.resolve("secrets/card.yaml"), "path-card", "pcsc");

    Profile profile = ProfileLoader.load("secrets/card.yaml", projectDir);

    assertEquals("path-card", profile.name());
    assertEquals("pcsc", profile.runtime());
  }

  /**
   * Writes a profile in one directory and loads it by its absolute path while passing an unrelated
   * project directory.
   *
   * <p>This checks that an absolute path is used as supplied, bypassing both the profile directory
   * lookup and the project-relative resolution.
   *
   * <p>Benefit: callers can load a profile kept outside the project tree.
   */
  @DisplayName("An absolute profile path is used as supplied.")
  @Test
  void loadsProfileByAbsolutePath() throws IOException {
    Path profileFile = projectDir.resolve("outside.yaml");
    writeProfile(profileFile, "absolute-card", "pcsc");

    Profile profile =
        ProfileLoader.load(profileFile.toAbsolutePath().toString(), projectDir.resolve("unused"));

    assertEquals("absolute-card", profile.name());
    assertEquals("pcsc", profile.runtime());
  }

  /**
   * Loads a profile name that has no file in the project but exists as a test resource on the
   * classpath.
   *
   * <p>This checks that the classpath fallback finds the bundled profile and reads its values.
   *
   * <p>Benefit: bundled default profiles stay usable without a project-local file.
   */
  @DisplayName("A named profile falls back to the classpath when no file is found.")
  @Test
  void loadsProfileFromClasspath() throws IOException {
    // jcdksim.yaml lives in this module's test resources under profile/.
    Profile profile = ProfileLoader.load("jcdksim", projectDir);

    assertEquals("jcdksim", profile.name());
    assertEquals("jcdksim", profile.runtime());
  }

  /**
   * Tries to load a name that exists in none of the searched locations and inspects the resulting
   * error message.
   *
   * <p>This checks that the message lists the project-local file path and the classpath location,
   * and that it does not mention the unsupported legacy directory.
   *
   * <p>Benefit: the error tells the user exactly where to put a missing profile.
   */
  @DisplayName("A missing profile error reports every searched location.")
  @Test
  void reportsAllSearchedLocations() {
    IOException exception =
        assertThrows(IOException.class, () -> ProfileLoader.load("missing", projectDir));

    String message = exception.getMessage();
    assertTrue(
        message.contains(
            projectDir.resolve("profile/missing.yaml").toAbsolutePath().normalize().toString()));
    assertFalse(
        message.contains(
            projectDir
                .resolve("project/profile/missing.yaml")
                .toAbsolutePath()
                .normalize()
                .toString()));
    assertTrue(message.contains("classpath:profile/missing.yaml"));
  }

  private static void writeProfile(Path path, String name, String runtime) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(
        path,
        """
        schema: cinpo.profile.v1
        name: %s
        runtime: %s
        secureChannel:
          protocol: scp03
          isdAid: A000000151000000
          keyVersionNumber: 16
          keyIdentifier: 0
          securityLevel: 1
          encKey: "1111111111111111111111111111111111111111111111111111111111111111"
          macKey: "2222222222222222222222222222222222222222222222222222222222222222"
          dekKey: "3333333333333333333333333333333333333333333333333333333333333333"
        """
            .formatted(name, runtime));
  }
}
