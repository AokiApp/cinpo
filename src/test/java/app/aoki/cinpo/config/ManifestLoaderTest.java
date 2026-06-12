package app.aoki.cinpo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void load_AcceptsCurrentCinpoSchemaWithoutWarningPath() throws IOException {
        Path manifestFile = writeManifest("cinpo.applet.v1");

        AppletManifest manifest = ManifestLoader.load(manifestFile);

        assertEquals("com.example.cinpo.hello", manifest.basePackage());
        assertEquals("com.example.cinpo.hello", manifest.packageName());
        assertEquals("26.0", manifest.toolSdkVersion());
        assertEquals("3.0.4", manifest.targetApiVersion());
        assertEquals(1, manifest.applets().size());
        assertEquals("hello", manifest.applets().getFirst().id());
    }

    @Test
    void load_AcceptsLegacyCardemusSchemaForExistingApplications() throws IOException {
        Path manifestFile = writeManifest("cardemus.applet.v1");

        AppletManifest manifest = ManifestLoader.load(manifestFile);

        assertEquals("com.example.cinpo.hello", manifest.packageName());
        assertEquals("hello", manifest.applets().getFirst().id());
    }

    @Test
    void load_RejectsMissingManifestFile() {
        Path missing = tempDir.resolve("missing.yaml");

        IOException exception = assertThrows(IOException.class, () -> ManifestLoader.load(missing));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains(missing.toAbsolutePath().toString()));
    }

    private Path writeManifest(String schema) throws IOException {
        Path manifestFile = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestFile, """
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
                """.formatted(schema));
        return manifestFile;
    }
}
