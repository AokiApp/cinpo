package app.aoki.cinpo.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.ManifestLoader;

/**
 * Java helper methods used by the Gradle DSL plugin layer.
 */
public final class CinpoGradleSupport {

    private CinpoGradleSupport() {
    }

    /**
     * Resolves the Java Card API jar matching the applet manifest target API.
     */
    public static Object targetApiJar(Project project) {
        AppletManifest manifest = loadManifest(project, "applet compilation");
        String targetApiVersion = manifest.targetApiVersion();
        return project.getLayout().getBuildDirectory()
                .file("cinpo-cache/jcdk/tools/lib/api_classic-" + targetApiVersion + ".jar");
    }

    /**
     * Resolves companion bundle stub jars declared in the manifest.
     */
    public static List<Path> companionBundleJars(Project project) {
        AppletManifest manifest = loadManifest(project, "companion bundle resolution");
        return CompanionBundleSupport.companionBundleJars(project, manifest);
    }

    private static AppletManifest loadManifest(Project project, String purpose) {
        try {
            return ManifestLoader.load(project.file("manifest.yaml").toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Java Card manifest for " + purpose, e);
        }
    }
}
