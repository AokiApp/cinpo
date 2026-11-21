package app.aoki.cinpo.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the classpath-entry predicate used when flattening the appliance and the
 * manifest-driven Java Card API path.
 */
class CinpoGradleSupportTest {

    @TempDir
    Path tempDirectory;

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
        assertFalse(CinpoGradleSupport.isBundleableClasspathEntry(
                tempDirectory.resolve("missing.jar").toFile()));
    }

    @Test
    void targetApiJarUsesTheManifestTargetApiVersion() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(tempDirectory.toFile()).build();
        Files.writeString(tempDirectory.resolve("manifest.yaml"), """
                schema: cinpo.applet.v1

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
                """);

        Object target = CinpoGradleSupport.targetApiJar(project);
        assertTrue(target instanceof Provider<?>);
        Object targetFile = ((Provider<?>) target).get();
        assertTrue(targetFile instanceof RegularFile);
        assertEquals(
                tempDirectory.resolve("build/cinpo-cache/jcdk/tools/lib/api_classic-3.0.4.jar"),
                ((RegularFile) targetFile).getAsFile().toPath());
    }

    private Path write(String name) throws IOException {
        Path file = tempDirectory.resolve(name);
        Files.writeString(file, name);
        return file;
    }
}
