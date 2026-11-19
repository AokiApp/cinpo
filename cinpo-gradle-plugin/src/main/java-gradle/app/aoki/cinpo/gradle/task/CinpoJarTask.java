package app.aoki.cinpo.gradle.task;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.Jar;

/**
 * Builds the production CINPO appliance JAR for template projects.
 *
 * <p>The template has a complete runtime classpath after {@code classes} has run:
 * host-side task classes, generated CAP resources, {@code manifest.yaml}, CINPO
 * framework classes, Java Card tool support classes, the socket terminal provider,
 * and third-party libraries. This task flattens that classpath into a single
 * executable JAR instead of requiring a distribution directory with many companion
 * JARs.</p>
 *
 * <p>This is a regular Gradle {@link Jar} task. The plugin adds the classpath
 * trees with {@code from()}, so Gradle owns duplicate handling, entry order,
 * timestamps and up-to-date checks. CINPO-specific inputs are the merged
 * {@code META-INF/services} directory produced by
 * {@link MergeCinpoServiceFilesTask} and the configured simulator directory, which
 * the plugin maps under {@code cinpo-simulator/<os>-<arch>}.</p>
 */
public abstract class CinpoJarTask extends Jar {

    private static final String MAIN_CLASS = "app.aoki.cinpo.cli.CinpoCli";
    private static final String AUTOMATIC_MODULE_NAME = "app.aoki.cinpo";
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String API_EXPORT_ENTRY_PREFIX = "api_export_files_";

    private final ConfigurableFileCollection runtimeClasspath;

    /**
     * Creates an appliance task with the default archive name and location.
     */
    public CinpoJarTask() {
        setGroup("cinpo");
        setDescription("Builds a self-contained CINPO appliance JAR.");

        runtimeClasspath = getProject().getObjects().fileCollection();

        getArchiveFileName().convention("cinpo-appliance.jar");
        getDestinationDirectory().convention(getProject().getLayout().getBuildDirectory().dir("libs"));
        setPreserveFileTimestamps(false);
        setReproducibleFileOrder(true);
        // Directory entries add nothing at runtime and Gradle's empty-directory
        // inference would otherwise keep a bare java/ entry alive after its files
        // were excluded.
        setIncludeEmptyDirs(false);
        // The runtime classpath is already ordered. Keeping the first occurrence
        // copies project classes and resources before dependency copies, like the
        // previous hand-written writer did.
        setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);

        getManifest().getAttributes().put("Main-Class", MAIN_CLASS);
        getManifest().getAttributes().put("Automatic-Module-Name", AUTOMATIC_MODULE_NAME);

        exclude((Spec<FileTreeElement>) element -> shouldSkipEntry(element.getPath(), targetApiVersion()));
    }

    /**
     * Returns the resolved runtime classpath mirrored into the appliance.
     *
     * <p>It is also used to decide whether the jcresim runtime module is present,
     * which requires the bundled simulator directory.</p>
     *
     * @return directories and archives that provide the runtime classpath
     */
    @Classpath
    public ConfigurableFileCollection getRuntimeClasspath() {
        return runtimeClasspath;
    }

    /**
     * Returns the CINPO framework version recorded in the appliance manifest.
     *
     * @return declared core version
     */
    @Input
    @Optional
    public abstract Property<String> getCinpoVersion();

    /**
     * Returns the manifest target API used to keep Java Card export resources minimal.
     *
     * @return target Java Card API version, or empty when the project has no manifest
     */
    @Input
    @Optional
    public abstract Property<String> getTargetApiVersion();

    /**
     * Returns the configured simulator runtime directory to embed as classpath resources.
     *
     * @return the {@code build/cinpo-cache/jcsl/<os>-<arch>} directory produced by
     *         {@code prepareJcresimSimulator}, unset when the build never prepared a
     *         simulator
     */
    @InputDirectory
    @Optional
    public abstract DirectoryProperty getBundledSimulatorDirectory();

    /**
     * Writes the appliance after validating the simulator wiring and adding the
     * framework version attribute.
     */
    @Override
    protected void copy() {
        if (jcresimRuntimeOnClasspath() && !getBundledSimulatorDirectory().isPresent()) {
            throw new IllegalStateException("The jcresim runtime module is on the classpath but this build did not "
                    + "prepare a simulator. Declare app.aoki.cinpo:cinpo-runtime-jcresim directly in the main "
                    + "runtime scope (api, implementation, or runtimeOnly) so the Gradle plugin can prepare it; "
                    + "a dependency that only brings it in transitively is not enough.");
        }
        if (getBundledSimulatorDirectory().isPresent()) {
            Path simulatorDirectory = getBundledSimulatorDirectory().get().getAsFile().toPath();
            if (!Files.isDirectory(simulatorDirectory)) {
                getLogger().warn("CINPO simulator runtime was not bundled because it was not found: {}",
                        simulatorDirectory);
            }
        }
        if (getCinpoVersion().isPresent()) {
            getManifest().getAttributes().put("Cinpo-Version", getCinpoVersion().get());
        }
        super.copy();
    }

    /**
     * Reports whether the jcresim runtime module contributes to the appliance.
     *
     * @return {@code true} when a {@code cinpo-runtime-jcresim} entry is present
     */
    private boolean jcresimRuntimeOnClasspath() {
        for (File file : getRuntimeClasspath().getFiles()) {
            if (file.getName().startsWith("cinpo-runtime-jcresim")) {
                return true;
            }
        }
        return false;
    }

    private String targetApiVersion() {
        return getTargetApiVersion().isPresent() ? getTargetApiVersion().get() : null;
    }

    /**
     * Determines whether an entry should be excluded from the merged appliance archive.
     *
     * @param entryName normalized archive entry name
     * @param targetApiVersion manifest target API version, or {@code null}
     * @return {@code true} when the entry is appliance-owned metadata or invalid after merging
     */
    private static boolean shouldSkipEntry(String entryName, String targetApiVersion) {
        // The appliance owns its manifest, and merged signed JAR metadata would make java -jar fail
        // with invalid signature errors because entries no longer match their original digests.
        // Module descriptors belong to the dependency that ships them; the appliance is not a
        // module, so it must not inherit one from a dependency — not from the root, and not from
        // a multi-release section. The java/ stubs jCardEngine ships for compiling applets are
        // never loaded at runtime (the bootstrap loader reads the JDK's own java/*), but they
        // split java.base if the JAR is ever placed on the module path.
        String upperName = entryName.toUpperCase(Locale.ROOT);
        return MANIFEST_ENTRY.equalsIgnoreCase(entryName)
                || upperName.endsWith(".SF")
                || upperName.endsWith(".DSA")
                || upperName.endsWith(".RSA")
                || isModuleInfoEntry(entryName)
                || entryName.startsWith("java/")
                || isApiExportEntryForOtherVersion(entryName, targetApiVersion);
    }

    /**
     * Matches a module descriptor at the archive root or inside a multi-release section such as
     * {@code META-INF/versions/9/module-info.class}.
     */
    private static boolean isModuleInfoEntry(String entryName) {
        if (entryName.equals("module-info.class")) {
            return true;
        }
        if (!entryName.startsWith("META-INF/versions/") || !entryName.endsWith("/module-info.class")) {
            return false;
        }
        int lastSlash = entryName.lastIndexOf('/');
        String release = entryName.substring("META-INF/versions/".length(), lastSlash);
        return !release.isEmpty() && release.chars().allMatch(Character::isDigit);
    }

    private static boolean isApiExportEntryForOtherVersion(String entryName, String targetApiVersion) {
        if (targetApiVersion == null || !entryName.startsWith(API_EXPORT_ENTRY_PREFIX)) {
            return false;
        }
        String allowedPrefix = API_EXPORT_ENTRY_PREFIX + targetApiVersion;
        // Directory entries arrive without the trailing slash. Excluding the target
        // directory itself would prune every file below it.
        return !entryName.equals(allowedPrefix) && !entryName.startsWith(allowedPrefix + "/");
    }
}
