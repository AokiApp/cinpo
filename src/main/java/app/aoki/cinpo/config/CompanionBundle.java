package app.aoki.cinpo.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Build-time companion bundle declared in {@code manifest.yaml}.
 *
 * <p>A companion bundle contributes two companion artifacts for applet builds:
 * <ul>
 *   <li>a stub {@code .jar} placed on the Java compiler classpath so applet sources can import
 *       vendor APIs, and</li>
 *   <li>an export directory passed to the Java Card converter so CAP generation can resolve the
 *       same off-card package metadata.</li>
 * </ul>
 *
 * <p>Both {@link #jar()} and {@link #exports()} are stored exactly as configured in the manifest.
 * Build-facing code may resolve them against a project directory with
 * {@link #resolveJar(Path)} and {@link #resolveExports(Path)}.
 *
 * @param id identifier used in diagnostics
 * @param jar path to the companion stub jar, relative to the project directory unless absolute
 * @param exports path to the companion export directory, relative to the project directory unless absolute
 */
public record CompanionBundle(
        String id,
        String jar,
        String exports
) {
    public CompanionBundle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(exports, "exports");

        if (id.isBlank()) {
            throw new IllegalArgumentException("companion bundle id must not be blank");
        }
        if (jar.isBlank()) {
            throw new IllegalArgumentException("companion bundle jar must not be blank");
        }
        if (exports.isBlank()) {
            throw new IllegalArgumentException("companion bundle exports must not be blank");
        }
    }

    /** Resolves {@link #jar()} against {@code projectDirectory} unless the configured path is absolute. */
    public Path resolveJar(Path projectDirectory) {
        return resolve(projectDirectory, jar);
    }

    /** Resolves {@link #exports()} against {@code projectDirectory} unless the configured path is absolute. */
    public Path resolveExports(Path projectDirectory) {
        return resolve(projectDirectory, exports);
    }

    private static Path resolve(Path projectDirectory, String configuredPath) {
        Objects.requireNonNull(projectDirectory, "projectDirectory");

        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return projectDirectory.resolve(path).normalize();
    }
}
