package app.aoki.cinpo.gradle.javacard;

import app.aoki.cinpo.gradle.javacard.tools.JavaCardToolException;
import com.sun.javacard.cinpo.OracleClassicCapBuilder;
import com.sun.javacard.cinpo.OracleClassicCapRequest;
import com.sun.javacard.cinpo.OracleClassicCapResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Small service boundary between CINPO Gradle code and Oracle Java Card internals.
 *
 * <p>The Gradle plugin calls this service, and this service calls the Oracle-package
 * CAP builder. That keeps the {@code app.aoki.*} side readable: it never needs to
 * know about parser, linker, CAP writer, converter profiles, or same-package access
 * tricks.</p>
 */
public final class OracleClassicCapBuildService {
    private OracleClassicCapBuildService() {
    }

    /**
     * Validates Gradle-facing inputs and delegates to the Oracle-package CAP builder.
     *
     * <p>This method is the only place in the {@code app.aoki.*} Gradle integration
     * layer that crosses into {@code com.sun.javacard.cinpo}. Keeping that crossing
     * here avoids leaking parser/linker/converter concepts into task code.</p>
     */
    public static OracleClassicCapResult build(CapBuildRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        verifyInputs(request);

        Path workDirectory = request.generatedResourcesRoot().getParent().resolve("cap-work");
        Files.createDirectories(workDirectory);
        Files.createDirectories(request.layout().resourceFile().getParent());

        OracleClassicCapRequest oracleRequest = new OracleClassicCapRequest(
                request.manifest(),
                request.companionExportDirectories(),
                request.compiledClassesRoot(),
                request.toolLibraryDirectory(),
                workDirectory,
                request.layout().resourceFile()
        );
        return OracleClassicCapBuilder.build(oracleRequest);
    }

    /** Ensures the project has the compiled classes and Oracle tool jar layout the builder expects. */
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
}
