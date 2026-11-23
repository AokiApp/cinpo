package app.aoki.cinpo.gp.cap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.util.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapLoaderTest {

  private static final List<String> COMPONENT_ORDER =
      List.of(
          "Header.cap",
          "Directory.cap",
          "Import.cap",
          "Applet.cap",
          "Class.cap",
          "Method.cap",
          "StaticField.cap",
          "ConstantPool.cap",
          "RefLocation.cap",
          "Export.cap",
          "Descriptor.cap");

  @TempDir Path tempDir;

  /**
   * Reads a CAP archive whose entries are stored out of order and with a small block size.
   *
   * <p>Verifies that the components are concatenated in canonical order, wrapped with C4 and its
   * length, then split into blocks no larger than the requested size, with the first and last
   * blocks checked explicitly. This protects the byte stream sent to the card, where order and
   * framing determine whether the applet loads at all.
   */
  @DisplayName(
      "Reading a CAP file concatenates components in canonical order, wraps them in C4, and splits them into blocks")
  @Test
  void concatenatesWrapsAndSplitsCap() throws IOException {
    Map<String, byte[]> components = minimalComponents();
    Path capFile =
        writeCapFile(
            "sample.cap",
            components,
            List.of(
                "Method.cap",
                "Header.cap",
                "Descriptor.cap",
                "Directory.cap",
                "Import.cap",
                "Applet.cap",
                "Class.cap",
                "StaticField.cap",
                "ConstantPool.cap",
                "RefLocation.cap",
                "Export.cap"));

    CapPackage capPackage = CapLoader.readCapFile(capFile, 7);

    byte[] concatenated = concatenateInCanonicalOrder(components);
    byte[] expectedLoadFileData = withC4Wrapper(concatenated);
    assertArrayEquals(expectedLoadFileData, capPackage.loadFileData());
    assertEquals((expectedLoadFileData.length + 6) / 7, capPackage.loadBlocks().size());
    assertTrue(capPackage.loadBlocks().stream().allMatch(block -> block.length <= 7));
    assertArrayEquals(slice(expectedLoadFileData, 0, 7), capPackage.loadBlocks().get(0));
    byte[] lastBlock = capPackage.loadBlocks().get(capPackage.loadBlocks().size() - 1);
    assertArrayEquals(
        slice(
            expectedLoadFileData,
            expectedLoadFileData.length - lastBlock.length,
            expectedLoadFileData.length),
        lastBlock);
  }

  /**
   * Reads a CAP archive and inspects the metadata exposed by CapPackage.
   *
   * <p>Verifies the package AID, major and minor versions, and the single applet AID. This makes
   * the registry's view of a CAP file explicit, so a metadata parsing regression is caught before
   * applet selection depends on it.
   */
  @DisplayName("Reading a CAP file parses the package and applet AIDs")
  @Test
  void parsesPackageAndAppletAids() throws IOException {
    Path capFile = writeCapFile("metadata.cap", minimalComponents(), COMPONENT_ORDER);

    CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

    assertTrue(capPackage.hasPackageAid());
    assertArrayEquals(hex("A0 00 00 01 51"), capPackage.packageAid());
    assertEquals(3, capPackage.majorVersion());
    assertEquals(4, capPackage.minorVersion());
    assertEquals(1, capPackage.appletAids().size());
    assertArrayEquals(hex("A0 00 00 01 51 01"), capPackage.appletAids().get(0));
  }

  /**
   * Reads a CAP archive and mutates the arrays and lists returned by its accessors.
   *
   * <p>Verifies that package AID, load file data, and load blocks are returned as defensive copies
   * and that the block list rejects modification. This prevents a caller from changing what will be
   * sent to the card after the package is parsed.
   */
  @DisplayName("CapPackage accessors return defensive copies of arrays and lists")
  @Test
  void copiesPackageArraysDefensively() throws IOException {
    Path capFile = writeCapFile("defensive.cap", minimalComponents(), COMPONENT_ORDER);
    CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

    byte[] packageAid = capPackage.packageAid();
    packageAid[0] = 0x00;
    assertArrayEquals(hex("A0 00 00 01 51"), capPackage.packageAid());

    byte[] loadFileData = capPackage.loadFileData();
    loadFileData[0] = 0x00;
    assertEquals(0xC4, capPackage.loadFileData()[0] & 0xFF);

    byte[] firstBlock = capPackage.loadBlocks().get(0);
    firstBlock[0] = 0x00;
    assertEquals(0xC4, capPackage.loadBlocks().get(0)[0] & 0xFF);
    assertThrows(
        UnsupportedOperationException.class, () -> capPackage.loadBlocks().add(new byte[] {0x01}));
  }

  /**
   * Reads a CAP archive with Export.cap and Descriptor.cap removed.
   *
   * <p>Verifies that the remaining components are still concatenated in canonical order, load
   * blocks and metadata stay available, and no exception is raised. This covers partial CAP files
   * produced by toolchains that omit optional components.
   */
  @DisplayName(
      "Reading a CAP file with missing components concatenates only the components that are present")
  @Test
  void toleratesMissingComponents() throws IOException {
    Map<String, byte[]> components = new LinkedHashMap<>(minimalComponents());
    components.remove("Export.cap");
    components.remove("Descriptor.cap");
    Path capFile = writeCapFile("missing-components.cap", components, COMPONENT_ORDER);

    CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

    byte[] expectedLoadFileData = withC4Wrapper(concatenateInCanonicalOrder(components));
    assertArrayEquals(expectedLoadFileData, capPackage.loadFileData());
    assertFalse(capPackage.loadBlocks().isEmpty());
    assertTrue(capPackage.hasPackageAid());
    assertEquals(1, capPackage.appletAids().size());
  }

  /**
   * Reads a CAP archive with Applet.cap removed.
   *
   * <p>Verifies that the load data is still produced and that the applet AID list is empty rather
   * than null. This pins the behavior for library CAP files, which contain no applet to select.
   */
  @DisplayName("A CAP file without Applet.cap still loads and reports no applet AIDs")
  @Test
  void loadsWithoutAppletComponent() throws IOException {
    Map<String, byte[]> components = new LinkedHashMap<>(minimalComponents());
    components.remove("Applet.cap");
    Path capFile = writeCapFile("missing-applet.cap", components, COMPONENT_ORDER);

    CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

    byte[] expectedLoadFileData = withC4Wrapper(concatenateInCanonicalOrder(components));
    assertArrayEquals(expectedLoadFileData, capPackage.loadFileData());
    assertTrue(capPackage.hasPackageAid());
    assertEquals(List.of(), capPackage.appletAids());
  }

  /**
   * Reads a CAP archive that contains none of the recognized components.
   *
   * <p>Verifies that reading fails with an IOException whose message names the missing known
   * components. This gives users an actionable error instead of an empty load stream that would be
   * sent to the card.
   */
  @DisplayName("Reading an archive with no known CAP components fails with a clear error")
  @Test
  void rejectsUnknownComponents() throws IOException {
    Path capFile = writeCapFile("empty.cap", Map.of(), List.of());

    IOException exception =
        assertThrows(IOException.class, () -> CapLoader.readCapFile(capFile, 255));
    assertTrue(exception.getMessage().contains("No known CAP components found"));
  }

  /**
   * Attempts to read a CAP file with block sizes 0 and 256.
   *
   * <p>Verifies that both values are rejected with IllegalArgumentException before the file is
   * opened. This keeps framing logic from failing later with an obscure error during installation.
   */
  @DisplayName("Reading a CAP file with a block size of 0 or 256 is rejected")
  @Test
  void rejectsInvalidBlockSize() {
    Path capFile = tempDir.resolve("unused.cap");

    assertThrows(IllegalArgumentException.class, () -> CapLoader.readCapFile(capFile, 0));
    assertThrows(IllegalArgumentException.class, () -> CapLoader.readCapFile(capFile, 256));
  }

  /**
   * Reads a CAP archive whose Header.cap and Applet.cap hold truncated metadata.
   *
   * <p>Verifies that metadata is reported as unavailable while load blocks are still produced. This
   * keeps loading usable for CAP files whose metadata a parser cannot understand, instead of
   * failing the whole installation.
   */
  @DisplayName("Reading a CAP file with unparseable metadata still produces load blocks")
  @Test
  void loadsWithUnparseableMetadata() throws IOException {
    Map<String, byte[]> components = minimalComponents();
    components.put("Header.cap", hex("01 00 04 00 00 00 00"));
    components.put("Applet.cap", hex("03 00 02 01 04"));
    Path capFile = writeCapFile("unparseable.cap", components, COMPONENT_ORDER);

    CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

    assertFalse(capPackage.hasPackageAid());
    assertEquals(-1, capPackage.majorVersion());
    assertEquals(-1, capPackage.minorVersion());
    assertEquals(List.of(), capPackage.appletAids());
    assertFalse(capPackage.loadBlocks().isEmpty());
  }

  private Path writeCapFile(
      String filename, Map<String, byte[]> components, List<String> zipEntryOrder)
      throws IOException {
    Path capFile = tempDir.resolve(filename);
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(capFile))) {
      for (String componentName : zipEntryOrder) {
        byte[] data = components.get(componentName);
        if (data == null) {
          continue;
        }
        output.putNextEntry(new ZipEntry("com/example/javacard/" + componentName));
        output.write(data);
        output.closeEntry();
      }
    }
    return capFile;
  }

  private static Map<String, byte[]> minimalComponents() {
    Map<String, byte[]> components = new LinkedHashMap<>();
    components.put("Header.cap", hex("01 00 10 DE CA FF ED 01 02 00 04 03 05 A0 00 00 01 51"));
    components.put("Directory.cap", hex("02 00 01 D2"));
    components.put("Import.cap", hex("04 00 01 14"));
    components.put("Applet.cap", hex("03 00 0A 01 06 A0 00 00 01 51 01 12 34"));
    components.put("Class.cap", hex("05 00 01 C5"));
    components.put("Method.cap", hex("06 00 01 66"));
    components.put("StaticField.cap", hex("07 00 01 57"));
    components.put("ConstantPool.cap", hex("08 00 01 C8"));
    components.put("RefLocation.cap", hex("09 00 01 99"));
    components.put("Export.cap", hex("0A 00 01 EA"));
    components.put("Descriptor.cap", hex("0B 00 01 DB"));
    return components;
  }

  private static byte[] concatenateInCanonicalOrder(Map<String, byte[]> components)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (String componentName : COMPONENT_ORDER) {
      byte[] data = components.get(componentName);
      if (data != null) {
        output.write(data);
      }
    }
    return output.toByteArray();
  }

  private static byte[] withC4Wrapper(byte[] value) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(0xC4);
    if (value.length <= 0x7F) {
      output.write(value.length);
    } else if (value.length <= 0xFF) {
      output.write(0x81);
      output.write(value.length);
    } else {
      output.write(0x82);
      output.write((value.length >>> 8) & 0xFF);
      output.write(value.length & 0xFF);
    }
    output.write(value);
    return output.toByteArray();
  }

  private static byte[] slice(byte[] data, int fromInclusive, int toExclusive) {
    return Arrays.copyOfRange(data, fromInclusive, toExclusive);
  }

  private static byte[] hex(String value) {
    return Util.parseHex(value);
  }
}
