package app.aoki.cinpo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionBundleTest {

  /**
   * Resolves a companion bundle's relative jar and exports paths against an absolute project
   * directory.
   *
   * <p>This test checks that both paths become absolute paths located under that project directory.
   *
   * <p>Benefit: the same manifest is used from different checkouts, so this test pins the project
   * directory as the single base for companion bundle lookup.
   */
  @Test
  @DisplayName(
      "Resolving a bundle's relative jar and exports paths should anchor them at the project directory")
  void resolvesPathsAgainstProjectDirectory() {
    CompanionBundle bundle =
        new CompanionBundle("gp", "vendor/gp/gpapi-globalplatform.jar", "vendor/gp/exports");

    assertEquals(
        Path.of("/tmp/project/vendor/gp/gpapi-globalplatform.jar"),
        bundle.resolveJar(Path.of("/tmp/project")));
    assertEquals(
        Path.of("/tmp/project/vendor/gp/exports"), bundle.resolveExports(Path.of("/tmp/project")));
  }

  /**
   * Constructs companion bundles with one blank field at a time.
   *
   * <p>This test checks that an empty id, jar, or exports path each causes
   * IllegalArgumentException.
   *
   * <p>Benefit: an empty path would otherwise resolve to the project directory itself, so rejecting
   * it at construction prevents a confusing file-not-found error later.
   */
  @Test
  @DisplayName(
      "Constructing a companion bundle with an empty id, jar, or exports path should be rejected")
  void rejectsBlankFields() {
    assertThrows(IllegalArgumentException.class, () -> new CompanionBundle("", "a.jar", "exports"));
    assertThrows(IllegalArgumentException.class, () -> new CompanionBundle("gp", "", "exports"));
    assertThrows(IllegalArgumentException.class, () -> new CompanionBundle("gp", "a.jar", ""));
  }
}
