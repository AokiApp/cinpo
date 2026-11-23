package app.aoki.cinpo.gradle.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the appliance JAR assembly rules: classpath order, metadata filtering and manifest
 * attributes.
 */
class CinpoJarTaskTest {

  @TempDir Path tempDirectory;

  /**
   * Builds an appliance JAR from two classpath roots that contain the same class and from a root
   * carrying module descriptors, JVM classes and signature files, then inspects the resulting
   * archive.
   *
   * <p>Confirms the three assembly rules named in the class documentation: classpath order picks
   * the first copy of a class, hostile metadata is filtered out, and the manifest carries the
   * expected main class, module name and version with a fixed entry timestamp.
   *
   * <p>Benefit: prevents a stale dependency from winning the class lookup, or a bundled {@code
   * module-info.class} or stale signature file from changing how the JVM loads the appliance.
   */
  @Test
  @DisplayName("The first classpath entry wins and hostile metadata is dropped from the JAR")
  void keepsFirstEntryDropsMetadata() throws Exception {
    Path projectDirectory = tempDirectory.resolve("project");
    Path classes = projectDirectory.resolve("classes");
    write(classes.resolve("example/first.txt"), "first");
    Path dependency = projectDirectory.resolve("dependency");
    write(dependency.resolve("example/first.txt"), "second");
    write(dependency.resolve("example/second.txt"), "second-only");
    write(dependency.resolve("module-info.class"), "module");
    write(dependency.resolve("java/lang/Object.class"), "stub");
    write(dependency.resolve("META-INF/MANIFEST.MF"), "input-manifest");
    write(dependency.resolve("META-INF/EXAMPLE.SF"), "signature");

    CinpoJarTask task = newTask(projectDirectory);
    task.from(classes.toFile());
    task.from(dependency.toFile());
    task.getCinpoVersion().set("9.9.9");
    copy(task);

    try (JarFile jar = new JarFile(task.getArchiveFile().get().getAsFile())) {
      assertEquals("first", read(jar, "example/first.txt"));
      assertEquals("second-only", read(jar, "example/second.txt"));
      assertNull(jar.getEntry("module-info.class"));
      assertNull(jar.getEntry("java/lang/Object.class"));
      assertNull(jar.getEntry("META-INF/EXAMPLE.SF"));
      assertEquals(
          "app.aoki.cinpo.cli.CinpoCli",
          jar.getManifest().getMainAttributes().getValue("Main-Class"));
      assertEquals(
          "app.aoki.cinpo",
          jar.getManifest().getMainAttributes().getValue("Automatic-Module-Name"));
      assertEquals("9.9.9", jar.getManifest().getMainAttributes().getValue("Cinpo-Version"));
      ZipEntry fixedTime = new ZipEntry("probe");
      fixedTime.setTime(
          LocalDateTime.of(1980, 2, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli());
      assertEquals(fixedTime.getTimeLocal(), jar.getEntry("example/first.txt").getTimeLocal());
    }
  }

  /**
   * Packages service descriptors whose file names end in {@code .SF} and {@code .DSA} together with
   * a real top-level signature file, then lists the archive entries.
   *
   * <p>Confirms that signature filtering looks at the path, so a service descriptor that merely
   * looks like a signature file survives while a genuine top-level signature file is removed.
   *
   * <p>Benefit: prevents an over-broad suffix filter from deleting service provider descriptors and
   * silently disabling a discovery-based feature of the appliance.
   */
  @Test
  @DisplayName("Signature-like service descriptors are kept while real signatures are removed")
  void keepsServiceSignatureLikeNames() throws Exception {
    Path projectDirectory = tempDirectory.resolve("project");
    Path dependency = projectDirectory.resolve("dependency");
    write(dependency.resolve("META-INF/services/com.example.SF"), "com.example.Impl");
    write(dependency.resolve("META-INF/services/com.example.DSA"), "com.example.Impl");
    write(dependency.resolve("META-INF/EXAMPLE.SF"), "signature");

    CinpoJarTask task = newTask(projectDirectory);
    task.from(dependency.toFile());
    copy(task);

    try (JarFile jar = new JarFile(task.getArchiveFile().get().getAsFile())) {
      assertTrue(jar.getEntry("META-INF/services/com.example.SF") != null);
      assertTrue(jar.getEntry("META-INF/services/com.example.DSA") != null);
      assertNull(jar.getEntry("META-INF/EXAMPLE.SF"));
    }
  }

  /**
   * Packages signature-like resources in a data directory and in a nested META-INF directory, plus
   * signature files at the top level of META-INF and in a lowercase meta-inf directory, then lists
   * the archive entries.
   *
   * <p>Confirms that filtering is limited to the top level of META-INF and leaves similarly named
   * resources in other locations untouched.
   *
   * <p>Benefit: prevents an application resource stored under a {@code .SF} name from being lost,
   * or a lowercase {@code meta-inf} directory from smuggling a stale signature into the appliance.
   */
  @Test
  @DisplayName("Resources outside top-level META-INF survive while signatures are dropped")
  void keepsNestedSignatureResources() throws Exception {
    Path projectDirectory = tempDirectory.resolve("project");
    Path dependency = projectDirectory.resolve("dependency");
    write(dependency.resolve("data/example.SF"), "resource");
    write(dependency.resolve("data/example.DSA"), "resource");
    write(dependency.resolve("data/example.RSA"), "resource");
    write(dependency.resolve("data/example.EC"), "resource");
    write(dependency.resolve("META-INF/nested/example.SF"), "resource");
    write(dependency.resolve("META-INF/EXAMPLE.SF"), "signature");
    write(dependency.resolve("meta-inf/lowercase.SF"), "signature");

    CinpoJarTask task = newTask(projectDirectory);
    task.from(dependency.toFile());
    copy(task);

    try (JarFile jar = new JarFile(task.getArchiveFile().get().getAsFile())) {
      assertEquals("resource", read(jar, "data/example.SF"));
      assertEquals("resource", read(jar, "data/example.DSA"));
      assertEquals("resource", read(jar, "data/example.RSA"));
      assertEquals("resource", read(jar, "data/example.EC"));
      assertEquals("resource", read(jar, "META-INF/nested/example.SF"));
      assertNull(jar.getEntry("META-INF/EXAMPLE.SF"));
      assertNull(jar.getEntry("meta-inf/lowercase.SF"));
    }
  }

  /**
   * Packages SF, DSA, RSA, upper- and lowercase EC signature files at the top level of META-INF
   * together with an EC-named service descriptor, then lists the archive entries.
   *
   * <p>Confirms that every supported signature metadata type, including the EC variant, is filtered
   * while a service descriptor with the same suffix is retained.
   *
   * <p>Benefit: prevents a signed dependency from leaving one signature type behind and causing JAR
   * verification to fail at load time, without sacrificing service descriptors.
   */
  @Test
  @DisplayName("Every signature metadata type including EC is removed while services remain")
  void dropsAllSignatureTypes() throws Exception {
    Path projectDirectory = tempDirectory.resolve("project");
    Path dependency = projectDirectory.resolve("dependency");
    write(dependency.resolve("META-INF/EXAMPLE.SF"), "signature");
    write(dependency.resolve("META-INF/EXAMPLE.DSA"), "signature");
    write(dependency.resolve("META-INF/EXAMPLE.RSA"), "signature");
    write(dependency.resolve("META-INF/EXAMPLE.EC"), "signature");
    write(dependency.resolve("META-INF/EXAMPLE.ec"), "signature");
    write(dependency.resolve("META-INF/services/com.example.EC"), "com.example.Impl");

    CinpoJarTask task = newTask(projectDirectory);
    task.from(dependency.toFile());
    copy(task);

    try (JarFile jar = new JarFile(task.getArchiveFile().get().getAsFile())) {
      assertNull(jar.getEntry("META-INF/EXAMPLE.SF"));
      assertNull(jar.getEntry("META-INF/EXAMPLE.DSA"));
      assertNull(jar.getEntry("META-INF/EXAMPLE.RSA"));
      assertNull(jar.getEntry("META-INF/EXAMPLE.EC"));
      assertNull(jar.getEntry("META-INF/EXAMPLE.ec"));
      assertTrue(jar.getEntry("META-INF/services/com.example.EC") != null);
    }
  }

  /**
   * Puts a jcdksim runtime artifact on the task's runtime classpath without providing a simulator,
   * then calls {@code copy()} on the task and captures the result.
   *
   * <p>Confirms that this invalid combination is rejected with {@link IllegalStateException} rather
   * than producing an appliance JAR that cannot run.
   *
   * <p>Benefit: gives a build author an immediate, explicit failure instead of a packaged runtime
   * that crashes only when a user launches it.
   */
  @Test
  @DisplayName("A jcdksim runtime on the classpath without a simulator makes the JAR task fail")
  void rejectsJcdksimWithoutSimulator() throws IOException {
    Path projectDirectory = tempDirectory.resolve("project");
    Path runtimeJar = projectDirectory.resolve("cinpo-runtime-jcdksim-0.5.1.jar");
    write(runtimeJar, "runtime");

    CinpoJarTask task = newTask(projectDirectory);
    task.getRuntimeClasspath().from(runtimeJar.toFile());

    assertThrows(IllegalStateException.class, task::copy);
  }

  private static void copy(CinpoJarTask task) throws IOException {
    Files.createDirectories(task.getDestinationDirectory().get().getAsFile().toPath());
    task.copy();
  }

  private CinpoJarTask newTask(Path projectDirectory) throws IOException {
    Files.createDirectories(projectDirectory);
    Project project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
    return project.getTasks().create("cinpoJar", CinpoJarTask.class);
  }

  private static String read(JarFile jar, String entryName) throws IOException {
    try (InputStream input = jar.getInputStream(jar.getEntry(entryName))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void write(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }
}
