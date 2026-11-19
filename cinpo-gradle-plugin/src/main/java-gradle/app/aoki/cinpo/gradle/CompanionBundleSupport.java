package app.aoki.cinpo.gradle;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.CompanionBundle;
import app.aoki.cinpo.gradle.javacard.tools.JavaCardToolException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.gradle.api.Project;

/**
 * Resolves companion bundle inputs declared in {@code manifest.yaml}.
 */
public final class CompanionBundleSupport {
    private CompanionBundleSupport() {
    }

    /**
     * Returns the companion stub jars to place on the applet compilation classpath.
     */
    public static List<Path> companionBundleJars(Project project, AppletManifest manifest) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(manifest, "manifest");
        return companionBundleJars(manifest, project.getProjectDir().toPath());
    }

    /**
     * Returns the companion stub jars to place on the applet compilation classpath.
     */
    public static List<Path> companionBundleJars(AppletManifest manifest, Path projectDirectory) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        return manifest.companionBundles().stream()
                .map(bundle -> requireRegularFile(bundle.resolveJar(projectDirectory), bundle, "jar"))
                .toList();
    }

    /**
     * Returns the companion export directories to pass to the converter.
     */
    public static List<Path> companionBundleExportDirectories(AppletManifest manifest, Path projectDirectory) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        return manifest.companionBundles().stream()
                .map(bundle -> requireDirectory(bundle.resolveExports(projectDirectory), bundle, "exports"))
                .toList();
    }

    private static Path requireRegularFile(Path path, CompanionBundle bundle, String fieldName) {
        if (!Files.isRegularFile(path)) {
            throw new JavaCardToolException("Companion bundle '" + bundle.id() + "' " + fieldName
                    + " file does not exist: " + path);
        }
        return path;
    }

    private static Path requireDirectory(Path path, CompanionBundle bundle, String fieldName) {
        if (!Files.isDirectory(path)) {
            throw new JavaCardToolException("Companion bundle '" + bundle.id() + "' " + fieldName
                    + " directory does not exist: " + path);
        }
        return path;
    }
}
