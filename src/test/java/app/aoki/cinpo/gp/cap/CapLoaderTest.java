package app.aoki.cinpo.gp.cap;

import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapLoaderTest {

    private static final List<String> COMPONENT_ORDER = List.of(
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
            "Descriptor.cap"
    );

    @TempDir
    Path tempDir;

    @Test
    void readCapFile_ConcatenatesCanonicalComponents_WrapsC4AndSplitsBlocks() throws IOException {
        Map<String, byte[]> components = minimalComponents();
        Path capFile = writeCapFile("sample.cap", components, List.of(
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
                "Export.cap"
        ));

        CapPackage capPackage = CapLoader.readCapFile(capFile, 7);

        byte[] concatenated = concatenateInCanonicalOrder(components);
        byte[] expectedLoadFileData = withC4Wrapper(concatenated);
        assertArrayEquals(expectedLoadFileData, capPackage.loadFileData());
        assertEquals((expectedLoadFileData.length + 6) / 7, capPackage.loadBlocks().size());
        assertTrue(capPackage.loadBlocks().stream().allMatch(block -> block.length <= 7));
        assertArrayEquals(slice(expectedLoadFileData, 0, 7), capPackage.loadBlocks().get(0));
        byte[] lastBlock = capPackage.loadBlocks().get(capPackage.loadBlocks().size() - 1);
        assertArrayEquals(slice(expectedLoadFileData, expectedLoadFileData.length - lastBlock.length, expectedLoadFileData.length),
                lastBlock);
    }

    @Test
    void readCapFile_ParsesPackageAndAppletAids() throws IOException {
        Path capFile = writeCapFile("metadata.cap", minimalComponents(), COMPONENT_ORDER);

        CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

        assertTrue(capPackage.hasPackageAid());
        assertArrayEquals(hex("A0 00 00 01 51"), capPackage.packageAid());
        assertEquals(3, capPackage.majorVersion());
        assertEquals(4, capPackage.minorVersion());
        assertEquals(1, capPackage.appletAids().size());
        assertArrayEquals(hex("A0 00 00 01 51 01"), capPackage.appletAids().get(0));
    }

    @Test
    void capPackage_AccessorsDefensivelyCopyArraysAndLists() throws IOException {
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
        assertThrows(UnsupportedOperationException.class, () -> capPackage.loadBlocks().add(new byte[]{0x01}));
    }

    @Test
    void readCapFile_ToleratesMissingComponentsAndConcatenatesPresentOnes() throws IOException {
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

    @Test
    void readCapFile_MissingAppletComponent_StillLoadsAndReturnsNoAppletAids() throws IOException {
        Map<String, byte[]> components = new LinkedHashMap<>(minimalComponents());
        components.remove("Applet.cap");
        Path capFile = writeCapFile("missing-applet.cap", components, COMPONENT_ORDER);

        CapPackage capPackage = CapLoader.readCapFile(capFile, 255);

        byte[] expectedLoadFileData = withC4Wrapper(concatenateInCanonicalOrder(components));
        assertArrayEquals(expectedLoadFileData, capPackage.loadFileData());
        assertTrue(capPackage.hasPackageAid());
        assertEquals(List.of(), capPackage.appletAids());
    }

    @Test
    void readCapFile_RejectsArchiveWithoutKnownComponents() throws IOException {
        Path capFile = writeCapFile("empty.cap", Map.of(), List.of());

        IOException exception = assertThrows(IOException.class, () -> CapLoader.readCapFile(capFile, 255));
        assertTrue(exception.getMessage().contains("No known CAP components found"));
    }

    @Test
    void readCapFile_RejectsInvalidBlockSize() {
        Path capFile = tempDir.resolve("unused.cap");

        assertThrows(IllegalArgumentException.class, () -> CapLoader.readCapFile(capFile, 0));
        assertThrows(IllegalArgumentException.class, () -> CapLoader.readCapFile(capFile, 256));
    }

    @Test
    void readCapFile_ToleratesUnparseableMetadataButStillLoadsBlocks() throws IOException {
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

    private Path writeCapFile(String filename, Map<String, byte[]> components, List<String> zipEntryOrder) throws IOException {
        Path capFile = tempDir.resolve(filename);
        try (ZipOutputStream output = new ZipOutputStream(java.nio.file.Files.newOutputStream(capFile))) {
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

    private static byte[] concatenateInCanonicalOrder(Map<String, byte[]> components) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (String componentName : COMPONENT_ORDER) {
            byte[] data = components.get(componentName);
            if (data != null) {
                output.write(data);
            }
        }
        return output.toByteArray();
    }

    private static byte[] withC4Wrapper(byte[] value) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
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
        return java.util.Arrays.copyOfRange(data, fromInclusive, toExclusive);
    }

    private static byte[] hex(String value) {
        return Util.parseHex(value);
    }
}
