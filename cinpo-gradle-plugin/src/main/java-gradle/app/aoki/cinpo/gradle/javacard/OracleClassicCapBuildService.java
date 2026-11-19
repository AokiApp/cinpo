package app.aoki.cinpo.gradle.javacard;

import app.aoki.cinpo.gradle.javacard.tools.JavaCardToolException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Service boundary between the Gradle plugin and the Oracle Java Card tools.
 *
 * <p>The service validates inputs, prepares the task-owned scratch directory and
 * runs the CAP builder inside a classloader that contains {@code tools.jar}. The
 * classloader is closed after each build, so no Oracle class or URL outlives the
 * task. See {@link CinpoJavaCardToolLoader} for the loading rules.</p>
 */
public final class OracleClassicCapBuildService {
    private OracleClassicCapBuildService() {
    }

    /**
     * Validates Gradle-facing inputs and runs the CAP builder.
     *
     * @param request runtime inputs collected by the Gradle task
     * @return paths to the Oracle output and the staged runtime resource
     * @throws Exception whatever the builder throws
     */
    public static CapBuildResult build(CapBuildRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        verifyInputs(request);

        Path workDirectory = request.workDirectory();
        deleteRecursively(workDirectory);
        Files.createDirectories(workDirectory);
        Files.createDirectories(request.layout().resourceFile().getParent());

        Path toolsJar = request.toolLibraryDirectory().resolve("tools.jar");
        try (CinpoJavaCardToolLoader loader = CinpoJavaCardToolLoader.open(toolsJar)) {
            return loader.build(request);
        }
    }

    /**
     * Ensures the project has the compiled classes and Oracle tool jar layout the
     * builder expects.
     */
    private static void verifyInputs(CapBuildRequest request) {
        if (!Files.isDirectory(request.compiledClassesRoot())) {
            throw new JavaCardToolException("Compiled class directory does not exist: " + request.compiledClassesRoot());
        }
        if (!Files.isDirectory(request.toolLibraryDirectory())) {
            throw new JavaCardToolException("Java Card tool library directory does not exist: " + request.toolLibraryDirectory());
        }
        Path toolsJar = request.toolLibraryDirectory().resolve("tools.jar");
        if (!Files.isRegularFile(toolsJar)) {
            throw new JavaCardToolException("Java Card tools.jar not found: " + toolsJar);
        }
    }

    /**
     * Removes a previous build's scratch directory so stale package trees from an
     * older manifest cannot be mistaken for output of the current build.
     */
    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
