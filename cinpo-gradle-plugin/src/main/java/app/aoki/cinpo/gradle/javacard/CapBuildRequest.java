package app.aoki.cinpo.gradle.javacard;

import app.aoki.cinpo.config.AppletManifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Gradle-facing request for Java Card CAP generation.
 *
 * <p>This DTO deliberately stays free of Oracle converter classes. It captures
 * only project concepts: manifest, compiled classes, template-local tool jars,
 * a converter work directory, and the target resource layout.</p>
 */
public record CapBuildRequest(
        AppletManifest manifest,
        List<Path> companionExportDirectories,
        Path compiledClassesRoot,
        Path toolLibraryDirectory,
        Path workDirectory,
        CapResourceLayout layout
) {
    public CapBuildRequest {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(companionExportDirectories, "companionExportDirectories");
        Objects.requireNonNull(compiledClassesRoot, "compiledClassesRoot");
        Objects.requireNonNull(toolLibraryDirectory, "toolLibraryDirectory");
        Objects.requireNonNull(workDirectory, "workDirectory");
        Objects.requireNonNull(layout, "layout");
        companionExportDirectories = List.copyOf(companionExportDirectories);
    }
}
