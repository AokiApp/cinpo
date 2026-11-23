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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the service-file merge that feeds the appliance JAR. */
class MergeCinpoServiceFilesTaskTest {

  private static final String ENTRY = "META-INF/services/com.example.Service";
  private static final String NAME = "com.example.Service";
  private static final String SERVICE_PATH = ENTRY;

  @TempDir Path tempDirectory;

  /**
   * Feeds a service file with a comment, an inline comment, a blank line, and provider names into
   * collectServiceLines and compares the collected lines with the input.
   *
   * <p>This checks that collectServiceLines preserves every line in order, including comments and
   * blank lines, instead of parsing or normalizing the file.
   *
   * <p>Benefit: it pins the collected content to the source lines, so a collector that parses or
   * drops lines is caught even though ServiceLoader ignores comments and blank lines when it loads
   * providers.
   */
  @DisplayName("Service file lines pass through unchanged, including comments and blank lines.")
  @Test
  void keepsServiceLinesVerbatim() {
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

  /**
   * Collects two service files in sequence and compares the combined provider list with both files'
   * contents.
   *
   * <p>This checks that lines from separate files are appended in call order, so the first file's
   * provider stays ahead of the second file's provider.
   *
   * <p>Benefit: ServiceLoader returns providers in declaration order, so switching to an unordered
   * collection would silently change which provider wins; this test catches that ordering loss.
   */
  @DisplayName("Service lines from multiple files concatenate in call order.")
  @Test
  void concatenatesInCallOrder() {
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Beta\n".getBytes(StandardCharsets.UTF_8));
    assertEquals(
        List.of("com.example.Alpha", "com.example.Beta"), new ArrayList<>(merged.get(NAME)));
  }

  /**
   * Collects the same provider name twice across two service files and compares the resulting list
   * with the expected single copy.
   *
   * <p>This checks that a repeated provider keeps its first position and appears only once in the
   * merged output.
   *
   * <p>Benefit: duplicate entries would make ServiceLoader instantiate the same provider twice and
   * can trigger duplicate-provider errors; this test guards the deduplication that prevents them.
   */
  @DisplayName("Duplicate service lines collapse to their first occurrence.")
  @Test
  void collapsesDuplicateLines() {
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "com.example.Alpha\ncom.example.Beta\n".getBytes(StandardCharsets.UTF_8));
    assertEquals(
        List.of("com.example.Alpha", "com.example.Beta"), new ArrayList<>(merged.get(NAME)));
  }

  /**
   * Collects a service file whose first provider name starts with a U+FEFF byte order mark and
   * compares the collected line with that prefixed name.
   *
   * <p>This checks that the merge leaves the byte order mark attached to the provider name, as the
   * inline comment in this test explains, rather than silently repairing the entry.
   *
   * <p>Benefit: stripping the mark would make the merged JAR accept a provider that the original
   * JAR rejects, hiding a real provider-name defect; this test keeps the two behaviors identical.
   */
  @DisplayName("A byte order mark stays part of the provider name.")
  @Test
  void keepsByteOrderMark() {
    // ServiceLoader rejects a BOM-prefixed provider name, so such a jar already
    // fails on a plain classpath. The merge keeps that behavior instead of
    // silently repairing what the JDK refuses.
    LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
    MergeCinpoServiceFilesTask.collectServiceLines(
        merged, ENTRY, "\uFEFFcom.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of("\uFEFFcom.example.Alpha"), new ArrayList<>(merged.get(NAME)));
  }

  /**
   * Runs the merge task with a classes directory and a dependency JAR on the classpath, then reads
   * the merged service file from the output directory.
   *
   * <p>This checks that providers from directories and archives are combined in classpath order,
   * with the overlapping provider kept only once.
   *
   * <p>Benefit: source order decides provider priority in the appliance JAR, so reversing the two
   * sources would change runtime behavior without failing the build; this test pins that order.
   */
  @DisplayName("Directory and JAR service files merge in classpath order.")
  @Test
  void mergesInClasspathOrder() throws IOException {
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

  /**
   * Leaves a stale service file in the output directory, runs the merge with an empty classpath,
   * and checks whether the stale file is still present.
   *
   * <p>This checks that the task clears output files that no longer have a matching source on the
   * current classpath.
   *
   * <p>Benefit: without that cleanup an obsolete provider would remain in the merged output after
   * its dependency is removed and would still be discovered at runtime; this test prevents that.
   */
  @DisplayName("Service files left by a previous run are removed from the output directory.")
  @Test
  void removesStaleFiles() throws IOException {
    Path outputDirectory = tempDirectory.resolve("merged");
    write(outputDirectory.resolve("META-INF/services/stale.Service"), "stale\n");

    MergeCinpoServiceFilesTask task = newTask();
    task.getClasspath().from(tempDirectory.resolve("empty").toFile());
    task.getOutputDirectory().set(outputDirectory.toFile());

    task.mergeServiceFiles();

    assertEquals(false, Files.exists(outputDirectory.resolve("META-INF/services/stale.Service")));
  }

  /**
   * Builds a dependency JAR with a traversal service entry name and runs the merge task against it.
   *
   * <p>This checks that the task rejects the entry with an "invalid name" GradleException and that
   * no file appears at the traversal target outside the output directory.
   *
   * <p>Benefit: without the name check, a malicious dependency could overwrite a build file such as
   * build.gradle during an ordinary build; this test verifies both the rejection and the absence of
   * the written file.
   */
  @DisplayName("Service entries that escape the output directory are rejected.")
  @Test
  void rejectsEscapingServiceEntries() throws IOException {
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

  /**
   * Builds a dependency JAR whose service entry name uses backslashes for traversal and runs the
   * merge task against it.
   *
   * <p>This checks that backslash traversal is rejected with an "invalid name" GradleException and
   * that no file is written to the traversal target outside the output directory.
   *
   * <p>Benefit: a check limited to forward slashes would let a Windows-style traversal escape the
   * output directory and overwrite a build file; this test closes that specific gap.
   */
  @DisplayName("Service entry names using backslashes are rejected.")
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

  /**
   * Collects a service entry whose binary name contains a dollar sign and checks the resulting map.
   *
   * <p>This checks that the name is accepted and used as the map key without alteration.
   *
   * <p>Benefit: nested Java classes carry a dollar sign in their binary names, so rejecting or
   * rewriting that character would silently drop those providers from the merged service file; this
   * test keeps them discoverable.
   */
  @DisplayName("Service names containing dollar signs for nested classes are accepted.")
  @Test
  void acceptsDollarSigns() {
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
