package app.aoki.cinpo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CompanionBundleTest {

    @Test
    void resolveJarAndExports_ResolveRelativePathsAgainstProjectDirectory() {
        CompanionBundle bundle = new CompanionBundle(
                "gp",
                "vendor/gp/gpapi-globalplatform.jar",
                "vendor/gp/exports"
        );

        assertEquals(
                Path.of("/tmp/project/vendor/gp/gpapi-globalplatform.jar"),
                bundle.resolveJar(Path.of("/tmp/project"))
        );
        assertEquals(
                Path.of("/tmp/project/vendor/gp/exports"),
                bundle.resolveExports(Path.of("/tmp/project"))
        );
    }

    @Test
    void constructor_RejectsBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new CompanionBundle("", "a.jar", "exports"));
        assertThrows(IllegalArgumentException.class, () -> new CompanionBundle("gp", "", "exports"));
        assertThrows(IllegalArgumentException.class, () -> new CompanionBundle("gp", "a.jar", ""));
    }
}
