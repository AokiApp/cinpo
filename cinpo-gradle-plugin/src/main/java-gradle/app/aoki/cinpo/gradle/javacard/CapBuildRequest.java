package app.aoki.cinpo.gradle.javacard;

import app.aoki.cinpo.config.AppletManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Everything one CAP build needs, expressed only in plugin-side types.
 *
 * <p>The record deliberately avoids Oracle converter classes. The Java Card tool classloader loads
 * the builder, but it delegates {@code app.aoki.*} back to the plugin classloader, so this type has
 * the same identity on both sides of the boundary.
 *
 * @param manifest applet manifest
 * @param companionExportDirectories export directories contributed by companion bundles
 * @param compiledClassesRoot root of the separately compiled applet classes
 * @param toolLibraryDirectory directory holding {@code tools.jar} and the selected API jar
 * @param apiExportRoot root of the extracted {@code api_export_files_*} trees
 * @param workDirectory task-owned scratch directory for converter output
 * @param layout classpath-facing resource location of the generated CAP
 */
public record CapBuildRequest(
    AppletManifest manifest,
    List<Path> companionExportDirectories,
    Path compiledClassesRoot,
    Path toolLibraryDirectory,
    Path apiExportRoot,
    Path workDirectory,
    CapResourceLayout layout) {
  public CapBuildRequest {
    Objects.requireNonNull(manifest, "manifest");
    Objects.requireNonNull(companionExportDirectories, "companionExportDirectories");
    Objects.requireNonNull(compiledClassesRoot, "compiledClassesRoot");
    Objects.requireNonNull(toolLibraryDirectory, "toolLibraryDirectory");
    Objects.requireNonNull(apiExportRoot, "apiExportRoot");
    Objects.requireNonNull(workDirectory, "workDirectory");
    Objects.requireNonNull(layout, "layout");
    companionExportDirectories = List.copyOf(companionExportDirectories);
  }
}
