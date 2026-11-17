package app.aoki.cinpo.gradle.task;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.ManifestLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
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
    private static final Attributes.Name AUTOMATIC_MODULE_NAME_ATTRIBUTE =
            new Attributes.Name("Automatic-Module-Name");
    private static final String AUTOMATIC_MODULE_NAME = "app.aoki.cinpo";
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String BUNDLED_SIMULATOR_ENTRY_PREFIX = "cinpo-simulator";
    private static final String API_EXPORT_ENTRY_PREFIX = "api_export_files_";
    private static final String SERVICE_ENTRY_PREFIX = "META-INF/services/";

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
        String platform = detectPlatform();
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
        LinkedHashMap<String, LinkedHashSet<String>> mergedServices = new LinkedHashMap<>();
        String targetApiVersion = targetApiVersion();
        Manifest manifest = manifest();
        try (JarOutputStream output = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath)), manifest)) {
            writtenEntries.add(MANIFEST_ENTRY);
            for (File file : getClasspath().getFiles()) {
                Path path = file.toPath();
                if (Files.isDirectory(path)) {
                    writeDirectory(output, writtenEntries, path, "", targetApiVersion, mergedServices);
                    continue;
                }
                if (Files.isRegularFile(path) && isArchive(path)) {
                    writeArchive(output, writtenEntries, path, targetApiVersion, mergedServices);
                }
            }
            writeBundledSimulator(output, writtenEntries, mergedServices);
            writeMergedServices(output, writtenEntries, mergedServices);
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
    private void writeBundledSimulator(JarOutputStream output, Set<String> writtenEntries,
            LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
        // prepareJavaCardTools copies and configures jcsl into build/cinpo-cache/jcsl/<os>-<arch>.
        // The runtime side intentionally does not know about Gradle's build directory; it only looks
        // for classpath resources below cinpo-simulator/<os>-<arch>. Keep this mapping explicit here.
        String platform = detectPlatform();
        Path simulatorDirectory = getBundledSimulatorDirectory().get().getAsFile().toPath();
        if (!Files.isDirectory(simulatorDirectory)) {
            getLogger().warn("CINPO simulator runtime was not bundled because it was not found: {}", simulatorDirectory);
            return;
        }
        writeDirectory(output, writtenEntries, simulatorDirectory, BUNDLED_SIMULATOR_ENTRY_PREFIX + "/" + platform, null, mergedServices);
    }

    /**
     * Writes the merged {@code META-INF/services/<name>} files accumulated from every input.
     *
     * @param output archive stream being written
     * @param writtenEntries entry names already written to the archive
     * @param mergedServices services collected from every input, in classpath order
     * @throws IOException if the services section cannot be written
     */
    private static void writeMergedServices(JarOutputStream output, Set<String> writtenEntries,
            LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
        if (mergedServices.isEmpty()) {
            return;
        }
        for (Map.Entry<String, LinkedHashSet<String>> entry : mergedServices.entrySet()) {
            String targetName = SERVICE_ENTRY_PREFIX + entry.getKey();
            byte[] content = String.join("\n", entry.getValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            JarEntry targetEntry = new JarEntry(targetName);
            targetEntry.setTime(writeServicesTime());
            output.putNextEntry(targetEntry);
            output.write(content);
            output.closeEntry();
            writtenEntries.add(targetName);
        }
    }

    private static long writeServicesTime() {
        // Deterministic timestamp keeps the appliance reproducible in merges.
        return 0L;
    }

    /**
     * Collects one services entry into the merged-service bucket.
     *
     * @param mergedServices map from service name to lines, populated in place
     * @param entryName normalized entry name (already known to start with the services prefix)
     * @param content raw bytes of the services file
     */
    private static void collectServiceLines(LinkedHashMap<String, LinkedHashSet<String>> mergedServices,
            String entryName, byte[] content) {
        String serviceName = entryName.substring(SERVICE_ENTRY_PREFIX.length());
        LinkedHashSet<String> lines = mergedServices.computeIfAbsent(serviceName, key -> new LinkedHashSet<>());
        String[] raw = new String(content, java.nio.charset.StandardCharsets.UTF_8).split("[\r\n]+");
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            lines.add(trimmed);
        }
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
        // Fixed module name so the automatic module does not depend on the JAR file name.
        attributes.put(AUTOMATIC_MODULE_NAME_ATTRIBUTE, AUTOMATIC_MODULE_NAME);
        return manifest;
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
            String targetApiVersion,
            LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
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
                if (entryName.startsWith(SERVICE_ENTRY_PREFIX)) {
                    collectServiceLines(mergedServices, entryName, Files.readAllBytes(source));
                    continue;
                }
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
            String targetApiVersion,
            LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
        // Flatten dependency archives into the appliance. Signature metadata is filtered by
        // shouldSkipEntry because the original signatures are invalid after merging many archives.
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry sourceEntry;
            while ((sourceEntry = input.getNextEntry()) != null) {
                String entryName = normalizeEntryName(sourceEntry.getName());
                if (sourceEntry.isDirectory()) {
                    input.closeEntry();
                    continue;
                }
                if (entryName.startsWith(SERVICE_ENTRY_PREFIX)) {
                    collectServiceLines(mergedServices, entryName, input.readAllBytes());
                    input.closeEntry();
                    continue;
                }
                if (shouldSkipEntry(entryName, targetApiVersion) || !writtenEntries.add(entryName)) {
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
        return !entryName.startsWith(API_EXPORT_ENTRY_PREFIX + targetApiVersion + "/");
    }

    private static String detectPlatform() {
        // The plugin must know which OS/arch directory to bundle, but jcresim is
        // an optional dependency the consumer adds separately — keep this knowledge
        // local to the task rather than depending on the jcresim module.
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String osName = os.contains("linux") ? "linux"
                : os.contains("mac") || os.contains("darwin") ? "darwin"
                : os.contains("win") ? "windows"
                : null;
        if (osName == null) {
            throw new IllegalStateException("Unsupported operating system: " + os);
        }
        String archName = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64"
                : arch.contains("amd64") || arch.contains("x86_64") ? "amd64"
                : null;
        if (archName == null) {
            throw new IllegalStateException("Unsupported CPU architecture: " + arch);
        }
        return osName + "-" + archName;
    }
}
