package app.aoki.cinpo.apdu;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BER-TLV encoding/decoding utilities for ISO 7816 / ISO 8825 structures.
 */
public final class BerTlv {

    private BerTlv() {
    }

    // ─── BER length encoding ─────────────────────────────────────────────

    /**
     * Encodes a length value in BER definite-form (short or long, up to 65535).
     */
    public static byte[] encodeBerLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        if (length <= 0x7F) {
            return new byte[]{(byte) length};
        }
        if (length <= 0xFF) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        if (length <= 0xFFFF) {
            return new byte[]{(byte) 0x82, (byte) (length >>> 8), (byte) length};
        }
        throw new IllegalArgumentException("BER length exceeds supported range (65535): " + length);
    }

    /**
     * Writes a BER-encoded length directly to a stream.
     */
    public static void writeBerLength(ByteArrayOutputStream out, int length) {
        byte[] encoded = encodeBerLength(length);
        out.write(encoded, 0, encoded.length);
    }

    // ─── Short LV / BER LV append helpers ────────────────────────────────

    /**
     * Appends a 1-byte length + value to the output (short LV, max 255 bytes).
     */
    public static void appendShortLv(ByteArrayOutputStream out, byte[] value) {
        if (value.length > 0xFF) {
            throw new IllegalArgumentException("short LV value exceeds 255 bytes");
        }
        out.write(value.length);
        out.write(value, 0, value.length);
    }

    /**
     * Appends a BER-encoded length + value to the output.
     */
    public static void appendBerLv(ByteArrayOutputStream out, byte[] value) {
        writeBerLength(out, value.length);
        out.write(value, 0, value.length);
    }

    // ─── TLV parsing ─────────────────────────────────────────────────────

    /**
     * Parsed TLV entry with a copy of the value bytes.
     */
    public record Tlv(int tag, byte[] value) {
    }

    /**
     * Zero-copy view of a parsed BER-TLV structure within a backing byte array.
     *
     * <p>Unlike {@link Tlv} which copies the value, this record holds offsets
     * into the original buffer, enabling in-place mutation of the value bytes.
     *
     * @param tag         the parsed tag (1-byte or 2-byte)
     * @param valueOffset offset into the original buffer where the value starts
     * @param length      length of the value in bytes
     * @param totalLen    total bytes consumed by this TLV structure
     *                    (tag bytes + length bytes + value bytes)
     */
    public record TlvView(int tag, int valueOffset, int length, int totalLen) {
    }

    /**
     * Parses a single BER-TLV structure at the given offset.
     *
     * <p>Supports 1-byte and 2-byte tags, and BER definite-form length
     * encoding up to 65535 bytes.
     *
     * @param data   the byte array containing the TLV
     * @param offset the offset at which to start parsing
     * @return a {@link TlvView} describing the parsed structure
     * @throws IllegalStateException if the data is truncated or unsupported
     */
    public static TlvView parseTlvAt(byte[] data, int offset) {
        int start = offset;
        int first = data[offset++] & 0xFF;
        int tag;
        if ((first & 0x1F) == 0x1F) {
            if (offset >= data.length) {
                throw new IllegalStateException("TLV data ends before second tag byte");
            }
            tag = (first << 8) | (data[offset++] & 0xFF);
        } else {
            tag = first;
        }

        if (offset >= data.length) {
            throw new IllegalStateException("TLV data ends before length field");
        }

        int firstLengthByte = data[offset++] & 0xFF;
        int length;
        if ((firstLengthByte & 0x80) == 0) {
            length = firstLengthByte;
        } else {
            int lengthByteCount = firstLengthByte & 0x7F;
            if (lengthByteCount == 0 || lengthByteCount > 2) {
                throw new IllegalStateException(
                        "Unsupported TLV length encoding with " + lengthByteCount + " bytes");
            }
            if (offset + lengthByteCount > data.length) {
                throw new IllegalStateException("TLV data ends before long-form length is complete");
            }
            length = 0;
            for (int i = 0; i < lengthByteCount; i++) {
                length = (length << 8) | (data[offset++] & 0xFF);
            }
        }

        if (offset + length > data.length) {
            throw new IllegalStateException("TLV value length exceeds remaining data");
        }

        int totalLen = (offset - start) + length;
        return new TlvView(tag, offset, length, totalLen);
    }

    /**
     * Parses a byte array containing concatenated BER-TLV structures.
     */
    public static List<Tlv> parseTlvs(byte[] data) {
        ArrayList<Tlv> tlvs = new ArrayList<>();
        int index = 0;
        while (index < data.length) {
            TlvView view = parseTlvAt(data, index);
            tlvs.add(new Tlv(view.tag(), Arrays.copyOfRange(data, view.valueOffset(), view.valueOffset() + view.length())));
            index += view.totalLen();
        }
        return tlvs;
    }

    /**
     * Parses exactly one top-level TLV from the data.
     */
    public static Tlv parseSingleTlv(byte[] data, String label) {
        List<Tlv> tlvs = parseTlvs(data);
        if (tlvs.size() != 1) {
            throw new IllegalStateException(label + " must contain exactly one top-level TLV, found " + tlvs.size());
        }
        return tlvs.get(0);
    }
}
