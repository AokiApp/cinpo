package app.aoki.cinpo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestLoaderTest {

  @TempDir Path tempDir;

  /**
   * Loads the manifest helper written with the current cinpo.applet.v1 schema identifier.
   *
   * <p>This test checks the parsed base package, package name, SDK and API versions, applet list,
   * and an empty companion bundle list when the manifest declares no bundles.
   *
   * <p>Benefit: this is the happy path the other manifest tests build on, so it gives a clear
   * baseline when the parser changes.
   */
  @Test
  @DisplayName(
      "Loading a manifest with the current schema should populate the expected fields with no companion bundles")
  void loadsCurrentSchema() throws IOException {
    Path manifestFile = writeManifest("cinpo.applet.v1");

    AppletManifest manifest = ManifestLoader.load(manifestFile);

    assertEquals("com.example.cinpo.hello", manifest.basePackage());
    assertEquals("com.example.cinpo.hello", manifest.packageName());
    assertEquals("26.0", manifest.toolSdkVersion());
    assertEquals("3.0.4", manifest.targetApiVersion());
    assertEquals(1, manifest.applets().size());
    assertEquals("hello", manifest.applets().getFirst().id());
    assertEquals(0, manifest.companionBundles().size());
  }

  /**
   * Loads a manifest that declares one companion bundle with relative jar and exports paths.
   *
   * <p>This test checks the parsed bundle id and paths, and that resolveJar and resolveExports
   * anchor those paths at the directory that holds the manifest.
   *
   * <p>Benefit: companion bundles are shared between projects, so a wrong base directory would
   * break a build only after the manifest moved; this test fixes the anchor explicitly.
   */
  @Test
  @DisplayName(
      "Loading a manifest with a companion bundle should resolve its jar and exports at the manifest directory")
  void loadsCompanionBundles() throws IOException {
    Path manifestFile = tempDir.resolve("manifest.yaml");
    Files.createDirectories(
        tempDir.resolve("vendor/globalplatform/org.globalplatform-1.7/exports"));
    Files.writeString(
        manifestFile,
        """
        schema: cinpo.applet.v1

        basePackage: com.example.cinpo.hello
        toolSdkVersion: "26.0"
        targetApiVersion: "3.0.4"

        package:
          name: com.example.cinpo.hello
          aid: F00000000101
          version: "1.0"

        companionBundles:
          - id: globalplatform-1.7
            jar: vendor/globalplatform/org.globalplatform-1.7/gpapi-globalplatform.jar
            exports: vendor/globalplatform/org.globalplatform-1.7/exports

        applets:
          - id: hello
            className: com.example.cinpo.hello.HelloApplet
            classAid: F0000000010101
            instanceAid: F000000001010001
            privilege: "00"
        """);

    AppletManifest manifest = ManifestLoader.load(manifestFile);

    assertEquals(1, manifest.companionBundles().size());
    CompanionBundle bundle = manifest.companionBundles().getFirst();
    assertEquals("globalplatform-1.7", bundle.id());
    assertEquals(
        "vendor/globalplatform/org.globalplatform-1.7/gpapi-globalplatform.jar", bundle.jar());
    assertEquals("vendor/globalplatform/org.globalplatform-1.7/exports", bundle.exports());
    assertTrue(
        bundle
            .resolveJar(tempDir)
            .endsWith("vendor/globalplatform/org.globalplatform-1.7/gpapi-globalplatform.jar"));
    assertTrue(
        bundle
            .resolveExports(tempDir)
            .endsWith("vendor/globalplatform/org.globalplatform-1.7/exports"));
  }

  /**
   * Loads a manifest that declares two companion bundles with the same id.
   *
   * <p>This test checks that loading throws IllegalArgumentException and that the message names the
   * duplicate companion bundle id.
   *
   * <p>Benefit: duplicate ids make bundle lookup ambiguous, so an early error that names the id
   * keeps the failure next to the manifest that caused it.
   */
  @Test
  @DisplayName(
      "Loading a manifest with two companion bundles sharing an id should fail with a named error")
  void rejectsDuplicateBundleIds() throws IOException {
    Path manifestFile = tempDir.resolve("manifest.yaml");
    Files.writeString(
        manifestFile,
        """
        schema: cinpo.applet.v1

        basePackage: com.example.cinpo.hello
        toolSdkVersion: "26.0"
        targetApiVersion: "3.0.4"

        package:
          name: com.example.cinpo.hello
          aid: F00000000101
          version: "1.0"

        companionBundles:
          - id: gp
            jar: vendor/gp/one.jar
            exports: vendor/gp/one-exports
          - id: gp
            jar: vendor/gp/two.jar
            exports: vendor/gp/two-exports

        applets:
          - id: hello
            className: com.example.cinpo.hello.HelloApplet
            classAid: F0000000010101
            instanceAid: F000000001010001
            privilege: "00"
        """);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> ManifestLoader.load(manifestFile));
    assertTrue(exception.getMessage().contains("Duplicate companion bundle id"));
  }

  /**
   * Loads a manifest from a temporary directory path where no file exists.
   *
   * <p>This test checks that the call throws IOException and that the message includes the absolute
   * path of the missing file.
   *
   * <p>Benefit: a missing manifest is a common setup mistake, so naming the path in the error lets
   * the user fix it without tracing the loader's call stack.
   */
  @Test
  @DisplayName(
      "Loading a manifest from a path that does not exist should throw an IOException naming that path")
  void rejectsMissingManifest() {
    Path missing = tempDir.resolve("missing.yaml");

    IOException exception = assertThrows(IOException.class, () -> ManifestLoader.load(missing));

    assertTrue(exception.getMessage().contains(missing.toAbsolutePath().toString()));
  }

  private Path writeManifest(String schema) throws IOException {
    Path manifestFile = tempDir.resolve("manifest.yaml");
    Files.writeString(
        manifestFile,
        """
        schema: %s

        basePackage: com.example.cinpo.hello
        toolSdkVersion: "26.0"
        targetApiVersion: "3.0.4"

        package:
          name: com.example.cinpo.hello
          aid: F00000000101
          version: "1.0"

        applets:
          - id: hello
            className: com.example.cinpo.hello.HelloApplet
            classAid: F0000000010101
            instanceAid: F000000001010001
            privilege: "00"
        """
            .formatted(schema));
    return manifestFile;
  }
}
