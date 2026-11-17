package provision;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal DER encoder covering the fixed X.509 shape this template needs.
 *
 * <p>It supports universal tags, context-specific constructed tags, short- and
 * long-form lengths, and the four STRING/tag kinds used below. It is not a
 * general ASN.1 library and does not attempt to be one.
 */
final class Der {

    private static final int INTEGER = 0x02;
    private static final int BIT_STRING = 0x03;
    private static final int OCTET_STRING = 0x04;
    private static final int NULL = 0x05;
    private static final int OBJECT_IDENTIFIER = 0x06;
    private static final int UTF8_STRING = 0x0C;
    private static final int GENERALIZED_TIME = 0x18;
    private static final int SEQUENCE = 0x30;
    private static final int SET = 0x31;

    private static final DateTimeFormatter GENERALIZED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private Der() {
    }

    static byte[] integer(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("DER INTEGER requires a non-negative value");
        }
        byte[] encoded = value.toByteArray();
        return tlv(INTEGER, encoded);
    }

    static byte[] bitString(byte[] content) {
        byte[] value = new byte[content.length + 1];
        // Zero trailing bits.
        value[0] = 0x00;
        System.arraycopy(content, 0, value, 1, content.length);
        return tlv(BIT_STRING, value);
    }

    static byte[] octetString(byte[] content) {
        return tlv(OCTET_STRING, content.clone());
    }

    static byte[] nullDER() {
        return new byte[] {NULL, 0x00};
    }

    static byte[] objectIdentifier(String dotted) {
        String[] parts = dotted.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("OID needs at least two arcs: " + dotted);
        }
        List<Byte> encoded = new ArrayList<>();
        long first = Long.parseLong(parts[0]);
        long second = Long.parseLong(parts[1]);
        if (first > 2 || (first < 2 && second > 39) || second < 0) {
            throw new IllegalArgumentException("First two arcs must fit the leading byte: " + dotted);
        }
        encoded.add((byte) (40 * first + second));
        for (int i = 2; i < parts.length; i++) {
            long arc = Long.parseLong(parts[i]);
            if (arc < 0) {
                throw new IllegalArgumentException("OID arc must be non-negative: " + dotted);
            }
            appendBase128(encoded, arc);
        }
        byte[] value = new byte[encoded.size()];
        for (int i = 0; i < encoded.size(); i++) {
            value[i] = encoded.get(i);
        }
        return tlv(OBJECT_IDENTIFIER, value);
    }

    static byte[] utf8String(String value) {
        return tlv(UTF8_STRING, value.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] generalizedTime(Instant instant) {
        return tlv(GENERALIZED_TIME, GENERALIZED_TIME_FORMAT.format(instant).getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] sequence(byte[]... parts) {
        return tlv(SEQUENCE, concat(parts));
    }

    static byte[] set(byte[]... parts) {
        return tlv(SET, concat(parts));
    }

    /**
     * Writes a context-specific constructed tag, used for the explicit
     * {@code [0]} version and {@code [3]} extensions of a TBS certificate.
     */
    static byte[] explicit(int tagNumber, byte[] content) {
        if (tagNumber < 0 || tagNumber > 30) {
            throw new IllegalArgumentException("Explicit tag out of range: " + tagNumber);
        }
        return tlv(0xA0 | tagNumber, content);
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private static void appendBase128(List<Byte> out, long value) {
        int size = 0;
        for (long v = value; v != 0; v >>>= 7) {
            size++;
        }
        if (size == 0) {
            out.add((byte) 0);
            return;
        }
        int start = out.size();
        for (int i = size - 1; i >= 0; i--) {
            int shift = i * 7;
            byte b = (byte) ((value >>> shift) & 0x7F);
            if (i != 0) {
                b = (byte) (b | 0x80);
            }
            out.add(b);
        }
    }

    private static byte[] tlv(int tag, byte[] value) {
        byte[] length = encodeLength(value.length);
        byte[] out = new byte[1 + length.length + value.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(value, 0, out, 1 + length.length, value.length);
        return out;
    }

    private static byte[] encodeLength(int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }
        int bytes = 0;
        for (int v = length; v != 0; v >>>= 8) {
            bytes++;
        }
        byte[] encoded = new byte[1 + bytes];
        encoded[0] = (byte) (0x80 | bytes);
        for (int i = 0; i < bytes; i++) {
            encoded[1 + bytes - 1 - i] = (byte) ((length >>> (8 * i)) & 0xFF);
        }
        return encoded;
    }
}
