package app.aoki.cinpo.gradle.javacard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Checks that the Oracle Java Card tool classes stay out of the Gradle plugin classloader.
 *
 * <p>The test reads the prepared {@code tools.jar} path from a system property set by the build, so
 * it runs against the same vendor extraction the plugin compiles against.
 */
class CinpoJavaCardToolLoaderTest {

  private static final String TOOLS_JAR_PROPERTY = "cinpo.test.toolsJar";

  @Test
  void oracleClassesLiveOnlyInTheIsolatedLoader() throws Exception {
    Path toolsJar = Path.of(System.getProperty(TOOLS_JAR_PROPERTY, ""));
    assertTrue(Files.isRegularFile(toolsJar), "tools.jar not found: " + toolsJar);

    // The plugin classloader must not see the Oracle classes. If it does, the
    // bridge and the tools are not isolated and a daemon can leak one project's
    // SDK into another project.
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.sun.javacard.converter.ConversionProfile"));

    try (CinpoJavaCardToolLoader loader = CinpoJavaCardToolLoader.open(toolsJar)) {
      Class<?> builder =
          loader.classLoader().loadClass("com.sun.javacard.cinpo.OracleClassicCapBuilder");
      Class<?> converter =
          loader.classLoader().loadClass("com.sun.javacard.converter.ConversionProfile");

      assertNotNull(builder);
      assertFalse(
          builder.getClassLoader() == CinpoJavaCardToolLoader.class.getClassLoader(),
          "builder must not be defined by the plugin classloader");
      assertTrue(
          converter.getClassLoader() == builder.getClassLoader(),
          "the bridge and the Oracle classes must share one classloader");
    }
  }
}
