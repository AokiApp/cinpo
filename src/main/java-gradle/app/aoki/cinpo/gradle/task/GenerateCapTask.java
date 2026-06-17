package app.aoki.cinpo.gradle.task;

import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import com.sun.javacard.cinpo.OracleClassicCapResult;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.ManifestLoader;
import app.aoki.cinpo.gradle.javacard.CapBuildRequest;
import app.aoki.cinpo.gradle.javacard.CapResourceLayout;
import app.aoki.cinpo.gradle.javacard.OracleClassicCapBuildService;

/**
 * Builds the Java Card CAP file declared by {@code manifest.yaml} and stages it
 * as a regular Gradle resource.
 *
 * <p>
 * The runtime installer looks up CAP files using the classpath resource path
 * {@code cap/<packageName>.cap}. This task intentionally writes into a
 * generated resources root, not into the source tree, so Gradle can include the
 * result in the normal {@code processResources} output without polluting user
 * files.</p>
 *
 * <p>
 * This task does not know Oracle converter internals. Its job is limited to
 * Gradle input/output declaration, manifest loading, and calling the high-level
 * CAP build service. The in-process, wrapperless Java Card logic lives below
 * {@code com.sun.javacard.*} packages so package-private Oracle types can be
 * used without reflection.</p>
 */
public abstract class GenerateCapTask extends DefaultTask {

    public GenerateCapTask() {
        setGroup("cinpo");
        setDescription("Builds the Java Card CAP file and stages it at cap/<packageName>.cap.");
    }

    /**
     * Manifest that declares package name, package AID, applets and target API.
     *
     * <p>
     * Standard value expected from the CINPO plugin wiring:
     * {@code manifest.yaml} in the project root.</p>
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getManifestFile();

    /**
     * Project directory used only as a base path for resolving relative companion bundle paths from the manifest.
     */
    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    /**
     * Directory containing Java Card-compatible applet class files.
     *
     * <p>
     * This is the output of {@code compileAppletJava}, not Gradle's regular
     * {@code compileJava}. Host-side Java may target the project toolchain, but
     * CAP conversion consumes only the separately compiled applet bytecode.</p>
     *
     * <p>
     * Standard value expected from the CINPO plugin wiring:
     * {@code build/classes/java/applet}.</p>
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAppletClassesDirectory();

    /**
     * Template-local Oracle Java Card tool library directory.
     *
     * <p>
     * Standard value expected from the CINPO plugin wiring:
     * {@code build/cinpo-cache/jcdk/tools/lib}. For example, the task expects
     * {@code build/cinpo-cache/jcdk/tools/lib/tools.jar} to exist below this
     * directory.</p>
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getToolLibraryDirectory();

    /**
     * Generated resources root that becomes part of the main runtime classpath.
     *
     * <p>
     * Standard value expected from the CINPO plugin wiring:
     * {@code build/generated/cinpo/resources/main}. A package named
     * {@code app.aoki.cinpo.example} is staged as
     * {@code build/generated/cinpo/resources/main/cap/app.aoki.cinpo.example.cap}.</p>
     */
    @OutputDirectory
    public abstract DirectoryProperty getGeneratedResourcesDirectory();

    /**
     * Executes the CAP build as a Gradle task action.
     *
     * <p>
     * The method intentionally performs only build-facing orchestration:
     * resolve configured paths, load {@code manifest.yaml}, compute the runtime
     * CAP resource location, and hand off to
     * {@link OracleClassicCapBuildService}. Oracle Java Card converter and CAP
     * writer details remain behind the service boundary.</p>
     */
    @TaskAction
    public void generateCap() {
        Path manifestPath = getManifestFile().get().getAsFile().toPath();
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Path classRoot = getAppletClassesDirectory().get().getAsFile().toPath();
        Path toolLibrary = getToolLibraryDirectory().get().getAsFile().toPath();
        Path generatedResources = getGeneratedResourcesDirectory().get().getAsFile().toPath();

        try {
            // Manifest loading is kept in the Gradle task layer because manifest.yaml
            // is a project file, not an Oracle Java Card tool concept.
            AppletManifest manifest = ManifestLoader.load(manifestPath);

            // This layout is the contract with InstallPhase: the generated resource
            // must be loadable later as cap/<manifest.packageName>.cap.
            CapResourceLayout layout = CapResourceLayout.forManifest(manifest, generatedResources);
            CapBuildRequest request = new CapBuildRequest(manifest, projectDirectory, classRoot, toolLibrary, generatedResources, layout);
            OracleClassicCapResult result = OracleClassicCapBuildService.build(request);

            getLogger().lifecycle("Generated Java Card CAP resource {} from {}", layout.resourcePath(), result.capFile());
        } catch (IOException e) {
            throw new GradleException("Failed to read Java Card manifest for CAP generation: " + manifestPath, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Failed to generate Java Card CAP", e);
        }
    }
}
