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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the appliance JAR assembly rules: classpath order, metadata filtering and manifest
 * attributes.
 */
class CinpoJarTaskTest {

  @TempDir Path tempDirectory;

  @Test
  void keepsFirstClasspathEntryAndDropsHostileMetadata() throws Exception {
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

  @Test
  void keepsServiceEntriesWhoseNameLooksLikeASignatureFile() throws Exception {
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

  @Test
  void keepsSignatureLikeResourcesOutsideTopLevelMetaInf() throws Exception {
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

  @Test
  void dropsEverySignatureMetadataTypeIncludingEc() throws Exception {
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

  @Test
  void failsWhenJcdksimIsOnTheClasspathWithoutASimulator() throws IOException {
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
