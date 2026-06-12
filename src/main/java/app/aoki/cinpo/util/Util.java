package app.aoki.cinpo.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Domain-free general-purpose utilities.
 */
public final class Util {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private Util() {
    }

    // ─── Byte array concatenation ────────────────────────────────────────

    public static byte[] concat(byte[]... values) {
        int totalLength = 0;
        for (byte[] value : values) {
            totalLength += value.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    // ─── Hex formatting ──────────────────────────────────────────────────

    public static String toHex(byte[] value) {
        return HEX.formatHex(value);
    }

    // If you do int,byte -> hex string operation, use Integer.toHexString() and do not use String.format("%02X", byte) or similar, to avoid unnecessary string formatting and concatenation.

    // ─── Hex parsing ─────────────────────────────────────────────────────

    /**
     * Parses a hex string, ignoring case plus whitespace, underscores, and hyphens.
     */
    public static byte[] parseHex(String value) {
        return HEX.parseHex(Objects.requireNonNull(value).replaceAll("[\\s_-]+", ""));
    }

    // ─── Validation helpers ──────────────────────────────────────────────

    public static int requireByte(int value) {
        if (value < 0x00 || value > 0xFF) {
            throw new IllegalArgumentException("Byte value must be between 0x00 and 0xFF");
        }
        return value;
    }

    // ─── Platform helpers ────────────────────────────────────────────────

    public static String detectOS() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        if (os.contains("win")) return "windows";
        throw new IllegalStateException("Unsupported operating system: " + os);
    }

    public static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
        if (arch.contains("amd64") || arch.contains("x86_64")) return "amd64";
        throw new IllegalStateException("Unsupported CPU architecture: " + arch);
    }

    public static boolean isWindows() {
        return "windows".equals(detectOS());
    }

    // ─── Binary read helpers ─────────────────────────────────────────────

    public static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static long readU4(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | ((long) (data[offset + 3] & 0xFF));
    }

    // ─── List copy helpers ───────────────────────────────────────────────

    /**
     * Creates a defensive deep copy of a {@code List<byte[]>}.
     */
    public static List<byte[]> copyByteArrayList(List<byte[]> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<byte[]> copies = new ArrayList<>(values.size());
        for (byte[] value : values) {
            copies.add(value == null ? null : Arrays.copyOf(value, value.length));
        }
        return List.copyOf(copies);
    }
}
