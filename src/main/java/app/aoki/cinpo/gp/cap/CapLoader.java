package app.aoki.cinpo.gp.cap;

import app.aoki.cinpo.apdu.BerTlv;
import app.aoki.cinpo.util.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a Java Card CAP archive and prepares GlobalPlatform LOAD command data per
 * GlobalPlatform Card Specification (GPCS) §11.6.
 *
 * <p>A CAP file is a ZIP archive containing Java Card VM component files ({@code *.cap}).
 * For GlobalPlatform loading, the relevant components are:
 * <ol>
 *   <li>Read from the ZIP in the canonical Java Card VM component order (§6 of the
 *       Java Card 3 VM Specification).</li>
 *   <li>Concatenated into a single byte stream.</li>
 *   <li>Wrapped in a BER-TLV structure using tag {@code 'C4'} (Load File Data Block),
 *       as defined in GPCS §11.6.2.3, Table 11-58.</li>
 *   <li>Split into fixed-size blocks, each transmitted via a separate LOAD command
 *       (GPCS §11.6.1: "Multiple LOAD commands may be used to transfer a Load File to
 *       the card").</li>
 * </ol>
 *
 * <p>Each LOAD block is numbered starting at {@code '00'} and incremented by one
 * (GPCS §11.6.1). P1 bit b8 signals whether more blocks follow or the current block
 * is the last (GPCS §11.6.2.1, Table 11-57).
 *
 * <p>All GP APDU command messages (excluding the APDU header) are limited to 255 bytes
 * (GPCS §11.1.5: "All GlobalPlatform APDU command messages … are limited to 255 bytes
 * in length"). Secure-messaging overhead (e.g. 8-byte C-MAC in SCP03) further reduces
 * the usable payload, hence the conservative {@link #DEFAULT_MAX_LOAD_BLOCK_SIZE}.
 */
public final class CapLoader {

    /**
     * Conservative maximum LOAD payload size (in bytes) when SCP03 C-MAC wrapping is active.
     *
     * <p>GPCS §11.1.5 states: "All GlobalPlatform APDU command messages (excluding the APDU
     * header) are limited to 255 bytes in length" (short APDU, Lc coded on one byte).
     * SCP03 appends an 8-byte C-MAC to the command data, so {@code 255 - 8 = 247} bytes
     * remain available for actual Load File payload per block.
     *
     * <p><b>Caveat:</b> This is a conservative value for SCP03 C-MAC. With SCP02 or no
     * secure channel, up to 255 bytes per block is possible.
     */
    public static final int DEFAULT_MAX_LOAD_BLOCK_SIZE = 247;

    /**
     * Canonical Java Card VM component concatenation order, per the Java Card 3.0.5 VM
     * Specification component ordering (Header=1, Directory=2, Import=3, Applet=4,
     * Class=5, Method=6, StaticField=7, ConstantPool=8, RefLocation=9, Descriptor=10).
     *
     * <p>Components must be concatenated in this exact order before wrapping in the
     * {@code C4} TLV (GPCS §11.6.2.3, Table 11-58).
     */
    private static final String[] COMPONENT_ORDER = {
        "Header.cap",      // component tag 0x01
        "Directory.cap",   // component tag 0x02
        "Import.cap",      // component tag 0x04, this intentionally comes before Applet.cap (tag 0x03), JCVM06Cap Table19, Table21
        "Applet.cap",      // component tag 0x03
        "Class.cap",       // component tag 0x05
        "Method.cap",      // component tag 0x06
        "StaticField.cap", // component tag 0x07
        "ConstantPool.cap",// component tag 0x08
        "RefLocation.cap", // component tag 0x09
        "Export.cap",      // component tag 0x0A
        "Descriptor.cap"   // component tag 0x0B
    };

    /** Java Card VM component tag for the Header component (JCVM spec §6.3). */
    private static final int TAG_HEADER_COMPONENT = 0x01;

    /** Java Card VM component tag for the Applet component (JCVM spec §6.6). */
    private static final int TAG_APPLET_COMPONENT = 0x03;

    /** Magic number ({@code 0xDECAFFED}) identifying a valid Java Card CAP Header component. */
    private static final long HEADER_MAGIC = 0xDECAFFEDL;

    /**
     * BER-TLV tag for the Load File Data Block, per GPCS §11.6.2.3, Table 11-58.
     *
     * <p>The table defines: {@code 'C4'} — Load File Data Block (Conditional). This tag
     * wraps the concatenated CAP components when the Load File is sent unencrypted.
     * If the Security Domain has the Ciphered Load File Data Block privilege, tag
     * {@code 'D4'} is used instead.
     */
    private static final int TAG_LOAD_FILE_DATA_BLOCK = 0xC4;

    private CapLoader() {
    }

    /**
     * Reads the CAP file at {@code capPath} and prepares LOAD command data using
     * {@link #DEFAULT_MAX_LOAD_BLOCK_SIZE} ({@value #DEFAULT_MAX_LOAD_BLOCK_SIZE} bytes)
     * as the maximum block size.
     *
     * @param capPath path to the {@code .cap} ZIP archive
     * @return a {@link CapPackage} containing all parsed metadata and split load blocks
     * @throws IOException if the file cannot be read or the archive contains no recognizable
     *                     CAP component data
     * @see #readCapFile(Path, int)
     */
    public static CapPackage readCapFile(Path capPath) throws IOException {
        return readCapFile(capPath, DEFAULT_MAX_LOAD_BLOCK_SIZE);
    }

    /**
     * Reads the CAP file at {@code capPath} and prepares LOAD command data.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Opens the ZIP archive and extracts each known {@code *.cap} component present in the
     *       archive.</li>
     *   <li>Concatenates the present components in the canonical JCVM order
     *       ({@link #COMPONENT_ORDER}).</li>
     *   <li>Wraps the concatenated bytes in a {@code 'C4'} BER-TLV structure (GPCS §11.6.2.3,
     *       Table 11-58: "Load File Data Block").</li>
     *   <li>Splits the wrapped data into blocks of at most {@code maxBlockSize} bytes;
     *       each block will be sent as one LOAD command (GPCS §11.6.1).</li>
     * </ol>
     *
     * <p>Additionally, the {@code Header.cap} component is parsed to extract the package AID
     * and version, and {@code Applet.cap} is parsed to extract applet AIDs. Parse failures
     * or missing metadata components are silently tolerated — the resulting fields will be
     * {@code null} or empty.
     *
     * @param capPath      path to the {@code .cap} ZIP archive
     * @param maxBlockSize maximum number of bytes per LOAD block (1–255 inclusive)
     * @return a {@link CapPackage} containing all parsed metadata and split load blocks
     * @throws IOException              if the file cannot be read or the archive contains no
     *                                  recognizable CAP component data
     * @throws IllegalArgumentException if {@code maxBlockSize} is outside [1, 255]
     */
    public static CapPackage readCapFile(Path capPath, int maxBlockSize) throws IOException {
        Objects.requireNonNull(capPath);
        if (maxBlockSize <= 0 || maxBlockSize > 255) {
            throw new IllegalArgumentException("maxBlockSize must be between 1 and 255: " + maxBlockSize);
        }

        try (InputStream in = Files.newInputStream(capPath)) {
            return readCapStream(in, capPath.toString(), maxBlockSize);
        }
    }

    /**
     * Reads a CAP file from the classpath and prepares LOAD command data using
     * {@link #DEFAULT_MAX_LOAD_BLOCK_SIZE} ({@value #DEFAULT_MAX_LOAD_BLOCK_SIZE} bytes)
     * as the maximum block size.
     *
     * <p>This method uses the context class loader to locate the resource. The resource path
     * should be relative to the classpath root (e.g., {@code "cap/myapplet.cap"}).
     *
     * @param resourcePath classpath-relative path to the {@code .cap} ZIP archive
     * @return a {@link CapPackage} containing all parsed metadata and split load blocks
     * @throws IOException if the resource cannot be found or read, or the archive contains no
     *                     recognizable CAP component data
     */
    public static CapPackage readFromClasspath(String resourcePath) throws IOException {
        Objects.requireNonNull(resourcePath);
        try (InputStream in = CapLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("CAP file not found on classpath: " + resourcePath
                        + ". Ensure the CAP file is included in the build output.");
            }
            return readCapStream(in, "classpath:/" + resourcePath, DEFAULT_MAX_LOAD_BLOCK_SIZE);
        }
    }

    private static CapPackage readCapStream(InputStream in, String sourceName, int maxBlockSize) throws IOException {
        Map<String, byte[]> components = readKnownComponents(in);
        if (components.isEmpty()) {
            throw new IOException("No known CAP components found in " + sourceName);
        }

        byte[] concatenatedComponents = concatenateComponents(components);
        if (concatenatedComponents.length == 0) {
            throw new IOException("CAP archive contains no component data in " + sourceName);
        }

        byte[] loadFileData = wrapInC4Tag(concatenatedComponents);
        List<byte[]> loadBlocks = splitIntoBlocks(loadFileData, maxBlockSize);

        ParsedHeader parsedHeader = tryParseHeader(components.get("Header.cap"));
        List<byte[]> appletAids = tryParseAppletAids(components.get("Applet.cap"));

        byte[] packageAid = parsedHeader == null ? null : parsedHeader.packageAid();
        int majorVersion = parsedHeader == null ? -1 : parsedHeader.majorVersion();
        int minorVersion = parsedHeader == null ? -1 : parsedHeader.minorVersion();

        return new CapPackage(packageAid, majorVersion, minorVersion, appletAids, loadFileData, loadBlocks);
    }

    private static Map<String, byte[]> readKnownComponents(InputStream in) throws IOException {
        Map<String, byte[]> components = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                for (String componentName : COMPONENT_ORDER) {
                    if (entryName.endsWith(componentName)) {
                        components.put(componentName, readZipEntryData(zis));
                        break;
                    }
                }
                zis.closeEntry();
            }
        }
        return components;
    }

    private static byte[] concatenateComponents(Map<String, byte[]> components) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String componentName : COMPONENT_ORDER) {
            byte[] data = components.get(componentName);
            if (data != null) {
                out.write(data);
            }
        }
        return out.toByteArray();
    }

    /**
     * Wraps {@code concatenatedComponents} in a {@code 'C4'} BER-TLV structure.
     *
     * <p>The length is encoded per ASN.1 BER-TLV rules (GPCS §11.1.5): 1 byte for
     * lengths up to 127; 2 bytes for lengths up to 255; 3 bytes for lengths up to 65535.
     */
    private static byte[] wrapInC4Tag(byte[] concatenatedComponents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TAG_LOAD_FILE_DATA_BLOCK);
        byte[] berLength = BerTlv.encodeBerLength(concatenatedComponents.length);
        out.write(berLength, 0, berLength.length);
        out.write(concatenatedComponents);
        return out.toByteArray();
    }

    /**
     * Splits {@code value} into sequential chunks of at most {@code blockSize} bytes.
     *
     * <p><b>Caveat:</b> Block count must not exceed 256 (P2 = {@code 0x00}–{@code 0xFF}),
     * as enforced by the caller per GPCS §11.6.2.2.
     */
    private static List<byte[]> splitIntoBlocks(byte[] value, int blockSize) {
        List<byte[]> blocks = new ArrayList<>((value.length + blockSize - 1) / blockSize);
        int offset = 0;
        while (offset < value.length) {
            int end = Math.min(offset + blockSize, value.length);
            blocks.add(Arrays.copyOfRange(value, offset, end));
            offset = end;
        }
        return List.copyOf(blocks);
    }

    private static byte[] readZipEntryData(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zis.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static ParsedHeader tryParseHeader(byte[] headerComponent) {
        if (headerComponent == null || headerComponent.length < 3) {
            return null;
        }

        int offset = 0;
        int tag = headerComponent[offset++] & 0xFF;
        if (tag != TAG_HEADER_COMPONENT) {
            return null;
        }

        int size = Util.readU2(headerComponent, offset);
        offset += 2;
        int end = Math.min(headerComponent.length, 3 + size);
        if (end - offset < 10) {
            return null;
        }

        long magic = Util.readU4(headerComponent, offset);
        offset += 4;
        if (magic != HEADER_MAGIC) {
            return null;
        }

        offset += 3; // minor_version, major_version, flags

        int packageMinor = headerComponent[offset++] & 0xFF;
        int packageMajor = headerComponent[offset++] & 0xFF;
        int aidLength = headerComponent[offset++] & 0xFF;
        if (aidLength < 5 || aidLength > 16 || offset + aidLength > end) {
            return null;
        }

        byte[] packageAid = Arrays.copyOfRange(headerComponent, offset, offset + aidLength);
        return new ParsedHeader(packageAid, packageMajor, packageMinor);
    }

    private static List<byte[]> tryParseAppletAids(byte[] appletComponent) {
        if (appletComponent == null || appletComponent.length < 4) {
            return List.of();
        }

        int offset = 0;
        int tag = appletComponent[offset++] & 0xFF;
        if (tag != TAG_APPLET_COMPONENT) {
            return List.of();
        }

        int size = Util.readU2(appletComponent, offset);
        offset += 2;
        int end = Math.min(appletComponent.length, 3 + size);
        if (offset >= end) {
            return List.of();
        }

        int count = appletComponent[offset++] & 0xFF;
        List<byte[]> appletAids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (offset >= end) {
                break;
            }

            int aidLength = appletComponent[offset++] & 0xFF;
            if (aidLength < 5 || aidLength > 16 || offset + aidLength + 2 > end) {
                break;
            }

            appletAids.add(Arrays.copyOfRange(appletComponent, offset, offset + aidLength));
            offset += aidLength;
            offset += 2; // install_method_offset
        }
        return List.copyOf(appletAids);
    }

    /**
     * Internal record for holding parsed header information.
     */
    private record ParsedHeader(byte[] packageAid, int majorVersion, int minorVersion) {
        private ParsedHeader {
            packageAid = Arrays.copyOf(packageAid, packageAid.length);
        }

        @Override
        public byte[] packageAid() {
            return Arrays.copyOf(packageAid, packageAid.length);
        }
    }
}
