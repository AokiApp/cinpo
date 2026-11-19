package app.aoki.cinpo.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalDependency;

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

    /**
     * Resolves companion bundle export directories declared in the manifest.
     */
    public static List<Path> companionBundleExportDirectories(Project project) {
        AppletManifest manifest = loadManifest(project, "companion export resolution");
        return CompanionBundleSupport.companionBundleExportDirectories(manifest, project.getProjectDir().toPath());
    }

    /**
     * Returns the version of the consumer's declared {@code app.aoki.cinpo:cinpo} core dependency.
     * The Gradle plugin wires the framework's annotation processor and the appliance's
     * {@code Cinpo-Version} manifest attribute from this declaration, so both always match the
     * framework the consumer's host code compiles against.
     *
     * @throws IllegalStateException when no concrete {@code app.aoki.cinpo:cinpo} version is declared
     */
    public static String declaredCinpoCoreVersion(Project project) {
        Configuration compileClasspath = project.getConfigurations().findByName("compileClasspath");
        if (compileClasspath != null) {
            List<Configuration> scope = new ArrayList<>(compileClasspath.getExtendsFrom());
            scope.add(compileClasspath);
            for (Configuration configuration : scope) {
                for (ExternalDependency dependency : configuration.getDependencies().withType(ExternalDependency.class)) {
                    if ("app.aoki.cinpo".equals(dependency.getGroup()) && "cinpo".equals(dependency.getName())) {
                        String version = dependency.getVersion();
                        if (version != null && !version.isBlank()) {
                            return version;
                        }
                        throw new IllegalStateException(
                                "app.aoki.cinpo:cinpo is declared without a concrete version. The CINPO Gradle "
                                        + "plugin wires the framework's annotation processor from this declaration, "
                                        + "so declare a concrete version, for example "
                                        + "implementation 'app.aoki.cinpo:cinpo:0.5.0'.");
                    }
                }
            }
        }
        throw new IllegalStateException(
                "This project does not declare the app.aoki.cinpo:cinpo dependency. The CINPO Gradle plugin wires "
                        + "the framework's annotation processor from your declared core dependency, so add "
                        + "implementation 'app.aoki.cinpo:cinpo:<version>' to the dependencies block.");
    }

    private static AppletManifest loadManifest(Project project, String purpose) {
        try {
            return ManifestLoader.load(project.file("manifest.yaml").toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Java Card manifest for " + purpose, e);
        }
    }
}
