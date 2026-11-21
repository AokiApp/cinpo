package app.aoki.cinpo.gradle.javacard;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the naming rules shared by the applet compilation classpath and the CAP
 * converter.
 */
class JavaCardApiLayoutTest {

    @Test
    void buildsApiJarNameAndPath() {
        Path toolLibrary = Path.of("build", "cinpo-cache", "jcdk", "tools", "lib");

        assertEquals("api_classic-3.0.4.jar", JavaCardApiLayout.apiJarName("3.0.4"));
        assertEquals(toolLibrary.resolve("api_classic-3.0.4.jar"),
                JavaCardApiLayout.apiJar(toolLibrary, "3.0.4"));
    }

    @Test
    void buildsApiExportNameAndPath() {
        Path apiExportRoot = Path.of("build", "cinpo-cache", "jcdk", "api-exports");

        assertEquals("api_export_files_3.0.5", JavaCardApiLayout.apiExportsName("3.0.5"));
        assertEquals(apiExportRoot.resolve("api_export_files_3.0.5"),
                JavaCardApiLayout.apiExports(apiExportRoot, "3.0.5"));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> JavaCardApiLayout.apiJarName(null));
        assertThrows(NullPointerException.class, () -> JavaCardApiLayout.apiJar(null, "3.0.4"));
        assertThrows(NullPointerException.class, () -> JavaCardApiLayout.apiJar(Path.of("lib"), null));
        assertThrows(NullPointerException.class, () -> JavaCardApiLayout.apiExportsName(null));
        assertThrows(NullPointerException.class, () -> JavaCardApiLayout.apiExports(null, "3.0.4"));
        assertThrows(NullPointerException.class, () -> JavaCardApiLayout.apiExports(Path.of("exports"), null));
    }
}
