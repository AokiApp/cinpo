package app.aoki.cinpo.apdu;

import app.aoki.cinpo.util.Util;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable response APDU value object.
 */
public final class ResponseApdu {

    private final byte[] data;
    private final int sw1;
    private final int sw2;

    public ResponseApdu(byte[] data, int sw1, int sw2) {
        this.data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        this.sw1 = Util.requireByte(sw1);
        this.sw2 = Util.requireByte(sw2);
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public int sw1() {
        return sw1;
    }

    public int sw2() {
        return sw2;
    }

    public int sw() {
        return (sw1 << 8) | sw2;
    }

    public byte[] toBytes() {
        byte[] bytes = Arrays.copyOf(data, data.length + 2);
        bytes[data.length] = (byte) sw1;
        bytes[data.length + 1] = (byte) sw2;
        return bytes;
    }

    public String toHexString() {
        return Util.toHex(toBytes());
    }

    @Override
    public String toString() {
        return toHexString();
    }

    public static ResponseApdu fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 2) {
            throw new IllegalArgumentException("Response APDU must be at least 2 bytes long");
        }

        byte[] data = Arrays.copyOf(bytes, bytes.length - 2);
        int sw1 = bytes[bytes.length - 2] & 0xFF;
        int sw2 = bytes[bytes.length - 1] & 0xFF;
        return new ResponseApdu(data, sw1, sw2);
    }

    public static ResponseApdu fromHexString(String hex) {
        return fromBytes(Util.parseHex(hex));
    }

}
