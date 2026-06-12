package app.aoki.cinpo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileLoaderTest {

    @TempDir
    Path projectDir;

    @Test
    void loadNamedProfile_ResolvesFromProjectLocalProfileDirectory() throws IOException {
        writeProfile(projectDir.resolve("profile/card1.yaml"), "card1", "pcsc");
        writeProfile(projectDir.resolve("project/profile/card1.yaml"), "legacy-card1", "jcresim");

        Profile profile = ProfileLoader.load("card1", projectDir);

        assertEquals("card1", profile.name());
        assertEquals("pcsc", profile.runtime());
    }

    @Test
    void loadNamedProfile_DoesNotSupportLegacyProjectProfileDirectory() throws IOException {
        writeProfile(projectDir.resolve("project/profile/card1.yaml"), "legacy-card1", "jcresim");

        IOException exception = assertThrows(IOException.class, () -> ProfileLoader.load("card1", projectDir));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains(projectDir.resolve("profile/card1.yaml").toAbsolutePath().normalize().toString()));
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(projectDir.resolve("project/profile/card1.yaml").toAbsolutePath().normalize().toString()));
    }

    @Test
    void loadPathLikeProfile_ResolvesRelativeToProjectDirectory() throws IOException {
        writeProfile(projectDir.resolve("secrets/card.yaml"), "path-card", "pcsc");

        Profile profile = ProfileLoader.load("secrets/card.yaml", projectDir);

        assertEquals("path-card", profile.name());
        assertEquals("pcsc", profile.runtime());
    }

    @Test
    void loadAbsoluteProfile_UsesSuppliedPathAsIs() throws IOException {
        Path profileFile = projectDir.resolve("outside.yaml");
        writeProfile(profileFile, "absolute-card", "pcsc");

        Profile profile = ProfileLoader.load(profileFile.toAbsolutePath().toString(), projectDir.resolve("unused"));

        assertEquals("absolute-card", profile.name());
        assertEquals("pcsc", profile.runtime());
    }

    @Test
    void loadNamedJcdksim_ReturnsBuiltinWhenNoProjectProfileExists() throws IOException {
        Profile profile = ProfileLoader.load("jcdksim", projectDir);

        assertEquals("jcdksim", profile.name());
        assertEquals("jcresim", profile.runtime());
    }

    @Test
    void loadMissingProfile_ReportsEverySearchedLocation() {
        IOException exception = assertThrows(IOException.class, () -> ProfileLoader.load("missing", projectDir));

        String message = exception.getMessage();
        org.junit.jupiter.api.Assertions.assertTrue(message.contains(projectDir.resolve("profile/missing.yaml").toAbsolutePath().normalize().toString()));
        org.junit.jupiter.api.Assertions.assertFalse(message.contains(projectDir.resolve("project/profile/missing.yaml").toAbsolutePath().normalize().toString()));
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("Available builtins: jcdksim"));
    }

    private static void writeProfile(Path path, String name, String runtime) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
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
                """.formatted(name, runtime));
    }
}
