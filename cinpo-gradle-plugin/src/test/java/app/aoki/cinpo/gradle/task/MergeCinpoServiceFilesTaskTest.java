package app.aoki.cinpo.gradle.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the service-file merge that feeds the appliance JAR. */
class MergeCinpoServiceFilesTaskTest {

  private static final String ENTRY = "META-INF/services/com.example.Service";
  private static final String NAME = "com.example.Service";
  private static final String SERVICE_PATH = ENTRY;

  @TempDir Path tempDirectory;

  @Test
  void linesPassThroughVerbatimIncludingComments() {
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged,
        ENTRY,
        ("# provider list\n" + "com.example.Alpha # default\n" + "\n" + "com.example.Beta\n")
            .getBytes(StandardCharsets.UTF_8));
    assertEquals(
        List.of("# provider list", "com.example.Alpha # default", "", "com.example.Beta"),
        new ArrayList<>(merged.get(NAME)));
  }

  @Test
  void multipleFilesConcatenateInCallOrder() {
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Beta\n".getBytes(StandardCharsets.UTF_8));
    assertEquals(
        List.of("com.example.Alpha", "com.example.Beta"), new ArrayList<>(merged.get(NAME)));
  }

  @Test
  void duplicateLinesCollapseToFirstOccurrence() {
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Alpha\ncom.example.Beta\n".getBytes(StandardCharsets.UTF_8));
    assertEquals(
        List.of("com.example.Alpha", "com.example.Beta"), new ArrayList<>(merged.get(NAME)));
  }

  @Test
  void byteOrderMarkStaysPartOfTheLine() {
    // ServiceLoader rejects a BOM-prefixed provider name, so such a jar already
    // fails on a plain classpath. The merge keeps that behavior instead of
    // silently repairing what the JDK refuses.
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "\uFEFFcom.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of("\uFEFFcom.example.Alpha"), new ArrayList<>(merged.get(NAME)));
  }

  @Test
  void mergesDirectoriesAndArchivesInClasspathOrder() throws IOException {
    Path classesDirectory = tempDirectory.resolve("classes");
    write(classesDirectory.resolve(SERVICE_PATH), "com.example.Alpha\ncom.example.Beta\n");

    Path dependency = tempDirectory.resolve("dependency.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(dependency))) {
      output.putNextEntry(new JarEntry(SERVICE_PATH));
      output.write("com.example.Beta\ncom.example.Gamma\n".getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }

    Path outputDirectory = tempDirectory.resolve("merged");
    MergeCinpoServiceFilesTask task = newTask();
    task.getClasspath().from(classesDirectory.toFile(), dependency.toFile());
    task.getOutputDirectory().set(outputDirectory.toFile());

    task.mergeServiceFiles();

    assertEquals(
        "com.example.Alpha\ncom.example.Beta\ncom.example.Gamma",
        Files.readString(outputDirectory.resolve(SERVICE_PATH), StandardCharsets.UTF_8));
  }

  @Test
  void removesFilesLeftByAPreviousRun() throws IOException {
    Path outputDirectory = tempDirectory.resolve("merged");
    write(outputDirectory.resolve("META-INF/services/stale.Service"), "stale\n");

    MergeCinpoServiceFilesTask task = newTask();
    task.getClasspath().from(tempDirectory.resolve("empty").toFile());
    task.getOutputDirectory().set(outputDirectory.toFile());

    task.mergeServiceFiles();

    assertEquals(false, Files.exists(outputDirectory.resolve("META-INF/services/stale.Service")));
  }

  @Test
  void rejectsServiceEntriesThatEscapeTheOutputDirectory() throws IOException {
    Path dependency = tempDirectory.resolve("malicious.jar");
    writeJar(dependency, "META-INF/services/../../../../../../build.gradle", "PWNED\n");

    Path outputDirectory = tempDirectory.resolve("merged");
    MergeCinpoServiceFilesTask task = newTask();
    task.getClasspath().from(dependency.toFile());
    task.getOutputDirectory().set(outputDirectory.toFile());

    GradleException exception = assertThrows(GradleException.class, task::mergeServiceFiles);
    assertTrue(exception.getMessage().contains("invalid name"));
    assertFalse(Files.exists(tempDirectory.resolve("build.gradle")));
  }

  @Test
  void rejectsBackslashServiceEntries() throws IOException {
    Path dependency = tempDirectory.resolve("malicious-backslash.jar");
    writeJar(dependency, "META-INF/services/..\\..\\..\\build.gradle", "PWNED\n");

    MergeCinpoServiceFilesTask task = newTask();
    task.getClasspath().from(dependency.toFile());
    task.getOutputDirectory().set(tempDirectory.resolve("merged").toFile());

    GradleException exception = assertThrows(GradleException.class, task::mergeServiceFiles);
    assertTrue(exception.getMessage().contains("invalid name"));
    assertFalse(Files.exists(tempDirectory.resolve("build.gradle")));
  }

  @Test
  void acceptsDollarSignsInServiceNames() {
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged,
        "META-INF/services/com.example.Outer$Inner",
        "com.example.Impl\n".getBytes(StandardCharsets.UTF_8));

    assertTrue(merged.containsKey("com.example.Outer$Inner"));
  }

  private MergeCinpoServiceFilesTask newTask() {
    Project project = ProjectBuilder.builder().withProjectDir(tempDirectory.toFile()).build();
    return project.getTasks().create("mergeCinpoServiceFiles", MergeCinpoServiceFilesTask.class);
  }

  private static void write(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }

  private static void writeJar(Path path, String entryName, String content) throws IOException {
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry(entryName));
      output.write(content.getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
  }
}
