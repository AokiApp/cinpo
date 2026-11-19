package app.aoki.cinpo.gradle.task;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Merges every {@code META-INF/services/<name>} file on a classpath into one
 * directory.
 *
 * <p>A single JAR can hold only one file per service name, so a flat appliance
 * must concatenate the service files of all inputs. {@code ServiceLoader} owns the
 * provider-configuration grammar when the merged file is read, so this task
 * preserves lines verbatim instead of re-implementing that parser. Concatenation
 * is correct by construction: whatever the JDK accepts in the original files, it
 * accepts in a single file holding the same lines.</p>
 */
public abstract class MergeCinpoServiceFilesTask extends DefaultTask {

    private static final String SERVICE_ENTRY_PREFIX = "META-INF/services/";

    public MergeCinpoServiceFilesTask() {
        setGroup("cinpo");
        setDescription("Merges META-INF/services files for the CINPO appliance JAR.");
    }

    /**
     * Returns the classpath whose service files are merged.
     *
     * @return directories and archives to scan
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the directory that receives the merged service files.
     *
     * @return output directory containing {@code META-INF/services/*}
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Scans the classpath in order and writes one merged file per service.
     */
    @TaskAction
    public void mergeServiceFiles() throws IOException {
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        deleteRecursively(outputDirectory);
        Files.createDirectories(outputDirectory);

        LinkedHashMap<String, LinkedHashSet<String>> mergedServices = new LinkedHashMap<>();
        for (File file : getClasspath().getFiles()) {
            Path path = file.toPath();
            if (Files.isDirectory(path)) {
                collectFromDirectory(path, mergedServices);
            } else if (Files.isRegularFile(path) && isArchive(path)) {
                collectFromArchive(path, mergedServices);
            }
        }
        writeMergedServices(outputDirectory, mergedServices);
    }

    private static void collectFromDirectory(
            Path root, LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
        Path servicesDirectory = root.resolve(SERVICE_ENTRY_PREFIX);
        if (!Files.isDirectory(servicesDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(servicesDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String serviceName = servicesDirectory.relativize(file).toString()
                        .replace(File.separatorChar, '/');
                collectServiceLines(mergedServices, SERVICE_ENTRY_PREFIX + serviceName, Files.readAllBytes(file));
            }
        }
    }

    private static void collectFromArchive(
            Path archive, LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
        try (ZipInputStream input = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                if (!entry.isDirectory() && entryName.startsWith(SERVICE_ENTRY_PREFIX)) {
                    collectServiceLines(mergedServices, entryName, input.readAllBytes());
                }
                input.closeEntry();
            }
        }
    }

    /**
     * Collects one services entry into the merged-service bucket, passing every line
     * through verbatim.
     *
     * @param mergedServices map from service name to lines, populated in place
     * @param entryName normalized entry name (already known to start with the services prefix)
     * @param content raw bytes of the services file
     */
    static void collectServiceLines(LinkedHashMap<String, LinkedHashSet<String>> mergedServices,
            String entryName, byte[] content) {
        String serviceName = entryName.substring(SERVICE_ENTRY_PREFIX.length());
        LinkedHashSet<String> lines = mergedServices.computeIfAbsent(serviceName, key -> new LinkedHashSet<>());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeMergedServices(
            Path outputDirectory, LinkedHashMap<String, LinkedHashSet<String>> mergedServices) throws IOException {
        for (Map.Entry<String, LinkedHashSet<String>> entry : mergedServices.entrySet()) {
            Path target = outputDirectory.resolve(SERVICE_ENTRY_PREFIX + entry.getKey());
            Files.createDirectories(target.getParent());
            String content = String.join("\n", entry.getValue());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
    }

    private static boolean isArchive(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".jar") || filename.endsWith(".zip");
    }

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
