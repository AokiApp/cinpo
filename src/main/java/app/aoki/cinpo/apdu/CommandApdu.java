package app.aoki.cinpo.apdu;

import app.aoki.cinpo.util.Util;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable command APDU value object with standard and extended-length support.
 */
public final class CommandApdu {

    private static final int HEADER_LENGTH = 4;
    private static final int MAX_SHORT_LC = 255;
    private static final int MAX_SHORT_LE = 256;
    private static final int MAX_EXTENDED_LC = 65_535;
    private static final int MAX_EXTENDED_LE = 65_536;

    private final int cla;
    private final int ins;
    private final int p1;
    private final int p2;
    private final byte[] data;
    private final Integer le;

    public CommandApdu(int cla, int ins, int p1, int p2) {
        this(cla, ins, p1, p2, null, null);
    }

    public CommandApdu(int cla, int ins, int p1, int p2, byte[] data) {
        this(cla, ins, p1, p2, data, null);
    }

    public CommandApdu(int cla, int ins, int p1, int p2, byte[] data, Integer le) {
        this.cla = Util.requireByte(cla);
        this.ins = Util.requireByte(ins);
        this.p1 = Util.requireByte(p1);
        this.p2 = Util.requireByte(p2);
        this.data = normalizeData(data);
        this.le = normalizeLe(le);
    }

    public int cla() {
        return cla;
    }

    public int ins() {
        return ins;
    }

    public int p1() {
        return p1;
    }

    public int p2() {
        return p2;
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public Integer le() {
        return le;
    }

    public boolean hasData() {
        return data.length > 0;
    }

    public boolean hasLe() {
        return le != null;
    }

    public boolean isExtendedLength() {
        return data.length > MAX_SHORT_LC || (le != null && le > MAX_SHORT_LE);
    }

    public byte[] toBytes() {
        boolean hasData = hasData();
        boolean hasLe = hasLe();
        boolean extended = isExtendedLength();

        int bodyLength = 0;
        if (hasData && hasLe) {
            bodyLength = extended ? 1 + 2 + data.length + 2 : 1 + data.length + 1;
        } else if (hasData) {
            bodyLength = extended ? 1 + 2 + data.length : 1 + data.length;
        } else if (hasLe) {
            bodyLength = extended ? 1 + 2 : 1;
        }

        byte[] apdu = new byte[HEADER_LENGTH + bodyLength];
        apdu[0] = (byte) cla;
        apdu[1] = (byte) ins;
        apdu[2] = (byte) p1;
        apdu[3] = (byte) p2;

        int offset = HEADER_LENGTH;
        if (hasData && hasLe) {
            if (extended) {
                apdu[offset++] = 0x00;
                writeUnsignedShort(apdu, offset, data.length);
                offset += 2;
                System.arraycopy(data, 0, apdu, offset, data.length);
                offset += data.length;
                writeUnsignedShort(apdu, offset, encodeExtendedLe(le));
            } else {
                apdu[offset++] = (byte) data.length;
                System.arraycopy(data, 0, apdu, offset, data.length);
                offset += data.length;
                apdu[offset] = (byte) encodeShortLe(le);
            }
            return apdu;
        }

        if (hasData) {
            if (extended) {
                apdu[offset++] = 0x00;
                writeUnsignedShort(apdu, offset, data.length);
                offset += 2;
                System.arraycopy(data, 0, apdu, offset, data.length);
            } else {
                apdu[offset++] = (byte) data.length;
                System.arraycopy(data, 0, apdu, offset, data.length);
            }
            return apdu;
        }

        if (hasLe) {
            if (extended) {
                apdu[offset++] = 0x00;
                writeUnsignedShort(apdu, offset, encodeExtendedLe(le));
            } else {
                apdu[offset] = (byte) encodeShortLe(le);
            }
        }

        return apdu;
    }

    public String toHexString() {
        return Util.toHex(toBytes());
    }

    @Override
    public String toString() {
        return toHexString();
    }

    public static CommandApdu fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Command APDU must be at least 4 bytes long");
        }

        int cla = unsigned(bytes[0]);
        int ins = unsigned(bytes[1]);
        int p1 = unsigned(bytes[2]);
        int p2 = unsigned(bytes[3]);

        if (bytes.length == HEADER_LENGTH) {
            return new CommandApdu(cla, ins, p1, p2);
        }

        if (bytes.length == HEADER_LENGTH + 1) {
            return new CommandApdu(cla, ins, p1, p2, null, decodeShortLe(unsigned(bytes[4])));
        }

        int index = HEADER_LENGTH;
        int firstBodyByte = unsigned(bytes[index]);
        if (firstBodyByte != 0x00) {
            int lc = firstBodyByte;
            index += 1;

            if (bytes.length == index + lc) {
                return new CommandApdu(cla, ins, p1, p2, Arrays.copyOfRange(bytes, index, index + lc), null);
            }
            if (bytes.length == index + lc + 1) {
                int le = decodeShortLe(unsigned(bytes[index + lc]));
                return new CommandApdu(cla, ins, p1, p2, Arrays.copyOfRange(bytes, index, index + lc), le);
            }

            throw new IllegalArgumentException("Invalid short command APDU structure");
        }

        index += 1;
        if (bytes.length < index + 2) {
            throw new IllegalArgumentException("Extended command APDU is missing a two-byte length field");
        }

        int lcOrLe = readUnsignedShort(bytes, index);
        index += 2;

        if (bytes.length == index) {
            return new CommandApdu(cla, ins, p1, p2, null, decodeExtendedLe(lcOrLe));
        }

        int lc = lcOrLe;
        if (lc == 0) {
            throw new IllegalArgumentException("Extended command APDU with data must not use Lc=0000");
        }
        if (bytes.length < index + lc) {
            throw new IllegalArgumentException("Extended command APDU data is shorter than declared Lc");
        }

        byte[] data = Arrays.copyOfRange(bytes, index, index + lc);
        index += lc;

        if (bytes.length == index) {
            return new CommandApdu(cla, ins, p1, p2, data, null);
        }
        if (bytes.length == index + 2) {
            return new CommandApdu(cla, ins, p1, p2, data, decodeExtendedLe(readUnsignedShort(bytes, index)));
        }

        throw new IllegalArgumentException("Invalid extended command APDU structure");
    }

    public static CommandApdu fromHexString(String hex) {
        return fromBytes(Util.parseHex(hex));
    }

    private static byte[] normalizeData(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        if (data.length > MAX_EXTENDED_LC) {
            throw new IllegalArgumentException("Command APDU data length must be between 1 and 65535 bytes");
        }
        return Arrays.copyOf(data, data.length);
    }

    private static Integer normalizeLe(Integer le) {
        if (le == null) {
            return null;
        }
        if (le < 1 || le > MAX_EXTENDED_LE) {
            throw new IllegalArgumentException("Command APDU Le must be between 1 and 65536 when present");
        }
        return le;
    }

    private static int encodeShortLe(int le) {
        if (le < 1 || le > MAX_SHORT_LE) {
            throw new IllegalArgumentException("Short command APDU Le must be between 1 and 256");
        }
        return le == MAX_SHORT_LE ? 0 : le;
    }

    private static int encodeExtendedLe(int le) {
        if (le < 1 || le > MAX_EXTENDED_LE) {
            throw new IllegalArgumentException("Extended command APDU Le must be between 1 and 65536");
        }
        return le == MAX_EXTENDED_LE ? 0 : le;
    }

    private static int decodeShortLe(int encodedLe) {
        return encodedLe == 0 ? MAX_SHORT_LE : encodedLe;
    }

    private static int decodeExtendedLe(int encodedLe) {
        return encodedLe == 0 ? MAX_EXTENDED_LE : encodedLe;
    }

    private static void writeUnsignedShort(byte[] target, int offset, int value) {
        target[offset] = (byte) ((value >>> 8) & 0xFF);
        target[offset + 1] = (byte) (value & 0xFF);
    }

    private static int readUnsignedShort(byte[] source, int offset) {
        return (unsigned(source[offset]) << 8) | unsigned(source[offset + 1]);
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

}
