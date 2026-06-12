package app.aoki.cinpo.gradle.task;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.ManifestLoader;
import app.aoki.cinpo.util.Util;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Builds the production CINPO appliance JAR for template projects.
 *
 * <p>The template already has a complete runtime classpath after {@code classes} has run: host-side
 * task classes, generated CAP resources, {@code manifest.yaml}, CINPO framework classes, Java Card
 * tool support classes, the socket terminal provider, and third-party libraries. This task flattens
 * that classpath into a single executable JAR instead of requiring a distribution directory with many
 * companion JARs.</p>
 *
 * <p>The JAR also embeds the configured Oracle simulator runtime under the resource layout consumed
 * by {@code JcreSimAdapter}: {@code cinpo-simulator/<os>-<arch>/...}. That keeps the README promise
 * that {@code java -jar build/libs/cinpo-appliance.jar write --profile jcdksim} works without first
 * preparing a Gradle build cache next to the appliance.</p>
 */
public abstract class CinpoJarTask extends DefaultTask {

    private static final String MAIN_CLASS = "app.aoki.cinpo.cli.CinpoCli";
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String BUNDLED_SIMULATOR_ENTRY_PREFIX = "cinpo-simulator";
    private static final String API_EXPORT_ENTRY_PREFIX = "api_export_files_";

    private final ConfigurableFileCollection classpath;
    private final DirectoryProperty bundledSimulatorDirectory;
    private final RegularFileProperty archiveFile;

    /**
     * Creates a task instance with the default appliance archive path and simulator cache path.
     */
    public CinpoJarTask() {
        setGroup("cinpo");
        setDescription("Builds a self-contained CINPO appliance JAR.");

        ObjectFactory objects = getProject().getObjects();
        classpath = objects.fileCollection();
        String platform = Util.detectOS() + "-" + Util.detectArch();
        bundledSimulatorDirectory = objects.directoryProperty().convention(
                getProject().getLayout().getBuildDirectory().dir("cinpo-cache/jcsl/" + platform));
        archiveFile = objects.fileProperty().convention(
                getProject().getLayout().getBuildDirectory().file("libs/cinpo-appliance.jar"));
    }

    /**
     * Returns the runtime classpath to flatten into the appliance JAR.
     *
     * @return directories and archives that provide project classes, resources, framework classes,
     *         Java Card tooling, and third-party runtime dependencies
     */
    @Classpath
    public ConfigurableFileCollection getClasspath() {
        return classpath;
    }

    /**
     * Returns the configured simulator runtime directory to embed as classpath resources.
     *
     * @return the {@code build/cinpo-cache/jcsl/<os>-<arch>} directory produced by
     *         {@code prepareJavaCardTools}
     */
    @InputDirectory
    @Optional
    public DirectoryProperty getBundledSimulatorDirectory() {
        return bundledSimulatorDirectory;
    }

    /**
     * Returns the appliance JAR output file.
     *
     * @return the {@code build/libs/cinpo-appliance.jar} output by default
     */
    @OutputFile
    public RegularFileProperty getArchiveFile() {
        return archiveFile;
    }

    /**
     * Executes appliance generation.
     */
    @TaskAction
    public void runTask() {
        Path outputPath = getArchiveFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(outputPath.getParent());
            Files.deleteIfExists(outputPath);
            writeJar(outputPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build CINPO appliance JAR: " + outputPath, e);
        }
        getLogger().lifecycle("Built CINPO appliance JAR: {}", getProject().relativePath(outputPath));
    }

    /**
     * Writes the executable fat JAR to the requested path.
     *
     * @param outputPath destination archive path
     * @throws IOException if any classpath entry cannot be read or the archive cannot be written
     */
    private void writeJar(Path outputPath) throws IOException {
        // Preserve first-wins classpath semantics. Gradle's runtimeClasspath is already ordered;
        // once an entry name is written, later duplicate entries are skipped. This avoids duplicate
        // ZIP entries while keeping project classes/resources ahead of dependency contents.
        Set<String> writtenEntries = new LinkedHashSet<>();
        String targetApiVersion = targetApiVersion();
        Manifest manifest = manifest();
        try (JarOutputStream output = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath)), manifest)) {
            writtenEntries.add(MANIFEST_ENTRY);
            for (File file : getClasspath().getFiles()) {
                Path path = file.toPath();
                if (Files.isDirectory(path)) {
                    writeDirectory(output, writtenEntries, path, targetApiVersion);
                    continue;
                }
                if (Files.isRegularFile(path) && isArchive(path)) {
                    writeArchive(output, writtenEntries, path, targetApiVersion);
                }
            }
            writeBundledSimulator(output, writtenEntries);
        }
    }

    /**
     * Reads the manifest target API used to keep Java Card export resources minimal.
     *
     * @return manifest-declared target Java Card API version
     * @throws IOException if {@code manifest.yaml} cannot be read
     */
    private String targetApiVersion() throws IOException {
        AppletManifest manifest = ManifestLoader.load(getProject().file("manifest.yaml").toPath());
        return manifest.targetApiVersion();
    }

    /**
     * Adds the configured simulator runtime to the resource namespace expected by the runtime.
     *
     * @param output archive stream being written
     * @param writtenEntries entry names already written to the archive
     * @throws IOException if simulator files cannot be copied into the archive
     */
    private void writeBundledSimulator(JarOutputStream output, Set<String> writtenEntries) throws IOException {
        // prepareJavaCardTools copies and configures jcsl into build/cinpo-cache/jcsl/<os>-<arch>.
        // The runtime side intentionally does not know about Gradle's build directory; it only looks
        // for classpath resources below cinpo-simulator/<os>-<arch>. Keep this mapping explicit here.
        String platform = Util.detectOS() + "-" + Util.detectArch();
        Path simulatorDirectory = getBundledSimulatorDirectory().get().getAsFile().toPath();
        if (!Files.isDirectory(simulatorDirectory)) {
            getLogger().warn("CINPO simulator runtime was not bundled because it was not found: {}", simulatorDirectory);
            return;
        }
        writeDirectory(output, writtenEntries, simulatorDirectory, BUNDLED_SIMULATOR_ENTRY_PREFIX + "/" + platform, null);
    }

    /**
     * Creates the appliance manifest used by {@code java -jar}.
     *
     * @return manifest declaring the CINPO CLI main class
     */
    private static Manifest manifest() {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        return manifest;
    }

    /**
     * Writes a directory tree using paths relative to its root.
     *
     * @param output archive stream being written
     * @param writtenEntries entry names already written to the archive
     * @param root directory whose files should be copied
     * @throws IOException if directory traversal or file copying fails
     */
    private static void writeDirectory(
            JarOutputStream output,
            Set<String> writtenEntries,
            Path root,
            String targetApiVersion) throws IOException {
        writeDirectory(output, writtenEntries, root, "", targetApiVersion);
    }

    /**
     * Writes a directory tree under an optional archive entry prefix.
     *
     * @param output archive stream being written
     * @param writtenEntries entry names already written to the archive
     * @param root directory whose files should be copied
     * @param entryPrefix archive path prefix, or an empty string to keep paths root-relative
     * @throws IOException if directory traversal or file copying fails
     */
    private static void writeDirectory(
            JarOutputStream output,
            Set<String> writtenEntries,
            Path root,
            String entryPrefix,
            String targetApiVersion) throws IOException {
        // Directory inputs are project outputs/resources. Their relative paths are already the
        // desired classpath paths, except for the simulator directory where entryPrefix relocates
        // native files into the resource namespace understood by JcreSimAdapter.
        try (var stream = Files.walk(root)) {
            for (Path source : stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(root::relativize))
                    .toList()) {
                String relativeName = normalizeEntryName(root.relativize(source));
                String entryName = entryPrefix.isBlank() ? relativeName : entryPrefix + "/" + relativeName;
                if (shouldSkipEntry(entryName, targetApiVersion) || !writtenEntries.add(entryName)) {
                    continue;
                }
                JarEntry entry = new JarEntry(entryName);
                entry.setTime(Files.getLastModifiedTime(source).toMillis());
                output.putNextEntry(entry);
                Files.copy(source, output);
                output.closeEntry();
            }
        }
    }

    /**
     * Expands a dependency archive into the appliance archive.
     *
     * @param output archive stream being written
     * @param writtenEntries entry names already written to the archive
     * @param archive dependency archive to flatten
     * @throws IOException if the dependency archive cannot be read or copied
     */
    private static void writeArchive(
            JarOutputStream output,
            Set<String> writtenEntries,
            Path archive,
            String targetApiVersion) throws IOException {
        // Flatten dependency archives into the appliance. Signature metadata is filtered by
        // shouldSkipEntry because the original signatures are invalid after merging many archives.
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry sourceEntry;
            while ((sourceEntry = input.getNextEntry()) != null) {
                String entryName = normalizeEntryName(sourceEntry.getName());
                if (sourceEntry.isDirectory() || shouldSkipEntry(entryName, targetApiVersion) || !writtenEntries.add(entryName)) {
                    input.closeEntry();
                    continue;
                }
                JarEntry targetEntry = new JarEntry(entryName);
                targetEntry.setTime(sourceEntry.getTime());
                output.putNextEntry(targetEntry);
                input.transferTo(output);
                output.closeEntry();
                input.closeEntry();
            }
        }
    }

    /**
     * Checks whether a regular file should be treated as an expandable archive.
     *
     * @param path file path to inspect
     * @return {@code true} for JAR or ZIP files
     */
    private static boolean isArchive(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".jar") || filename.endsWith(".zip");
    }

    /**
     * Converts a filesystem path into a portable JAR entry name.
     *
     * @param path path to convert
     * @return slash-separated archive entry name
     */
    private static String normalizeEntryName(Path path) {
        return normalizeEntryName(path.toString());
    }

    /**
     * Converts a raw path string into a portable JAR entry name.
     *
     * @param name path string to convert
     * @return slash-separated archive entry name
     */
    private static String normalizeEntryName(String name) {
        return name.replace(File.separatorChar, '/');
    }

    /**
     * Determines whether an entry should be excluded from the merged appliance archive.
     *
     * @param entryName normalized archive entry name
     * @return {@code true} when the entry is appliance-owned metadata or invalid after merging
     */
    private static boolean shouldSkipEntry(String entryName, String targetApiVersion) {
        // The appliance owns its manifest, and merged signed JAR metadata would make java -jar fail
        // with invalid signature errors because entries no longer match their original digests.
        String upperName = entryName.toUpperCase(Locale.ROOT);
        return MANIFEST_ENTRY.equalsIgnoreCase(entryName)
                || upperName.endsWith(".SF")
                || upperName.endsWith(".DSA")
                || upperName.endsWith(".RSA")
                || isApiExportEntryForOtherVersion(entryName, targetApiVersion);
    }

    private static boolean isApiExportEntryForOtherVersion(String entryName, String targetApiVersion) {
        if (targetApiVersion == null || !entryName.startsWith(API_EXPORT_ENTRY_PREFIX)) {
            return false;
        }
        return !entryName.startsWith(API_EXPORT_ENTRY_PREFIX + targetApiVersion + "/");
    }
}
