package app.aoki.cinpo.apdu;

import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandApduTest {

    @Test
    void case1HeaderOnly_RoundTrips() {
        CommandApdu command = new CommandApdu(0x00, 0xA4, 0x04, 0x00);

        assertFalse(command.hasData());
        assertFalse(command.hasLe());
        assertFalse(command.isExtendedLength());
        assertArrayEquals(hex("00 A4 04 00"), command.toBytes());

        CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
        assertEquals(0x00, parsed.cla());
        assertEquals(0xA4, parsed.ins());
        assertEquals(0x04, parsed.p1());
        assertEquals(0x00, parsed.p2());
        assertFalse(parsed.hasData());
        assertFalse(parsed.hasLe());
    }

    @Test
    void case2ShortLe256_UsesZeroLeEncoding() {
        CommandApdu command = new CommandApdu(0x80, 0x50, 0x00, 0x00, null, 256);

        assertTrue(command.hasLe());
        assertFalse(command.isExtendedLength());
        assertArrayEquals(hex("80 50 00 00 00"), command.toBytes());

        CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
        assertEquals(256, parsed.le());
    }

    @Test
    void case3ShortDataOnly_RoundTripsAndDefensivelyCopies() {
        byte[] data = hex("DE AD BE EF");
        CommandApdu command = new CommandApdu(0x80, 0xE8, 0x00, 0x02, data);
        data[0] = 0x00;

        assertArrayEquals(hex("80 E8 00 02 04 DE AD BE EF"), command.toBytes());

        byte[] returnedData = command.data();
        returnedData[1] = 0x00;
        assertArrayEquals(hex("DE AD BE EF"), command.data());

        CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
        assertArrayEquals(hex("DE AD BE EF"), parsed.data());
    }

    @Test
    void case4ShortDataAndLe_RoundTrips() {
        CommandApdu command = new CommandApdu(0x00, 0xCA, 0x01, 0x02, hex("5C 02 7F 21"), 256);

        assertArrayEquals(hex("00 CA 01 02 04 5C 02 7F 21 00"), command.toBytes());

        CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
        assertEquals(256, parsed.le());
        assertArrayEquals(hex("5C 02 7F 21"), parsed.data());
    }

    @Test
    void extendedDataOnly_UsesThreeByteLcAndRoundTrips() {
        byte[] data = sequence(256);
        CommandApdu command = new CommandApdu(0x80, 0xE8, 0x00, 0x00, data);

        byte[] encoded = command.toBytes();
        assertTrue(command.isExtendedLength());
        assertEquals(4 + 1 + 2 + 256, encoded.length);
        assertArrayEquals(hex("80 E8 00 00 00 01 00"), Arrays.copyOf(encoded, 7));

        CommandApdu parsed = CommandApdu.fromBytes(encoded);
        assertArrayEquals(data, parsed.data());
        assertFalse(parsed.hasLe());
    }

    @Test
    void extendedLe65536_UsesZeroExtendedLeEncoding() {
        CommandApdu command = new CommandApdu(0x00, 0xCA, 0x00, 0x00, null, 65_536);

        assertTrue(command.isExtendedLength());
        assertArrayEquals(hex("00 CA 00 00 00 00 00"), command.toBytes());

        CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
        assertEquals(65_536, parsed.le());
    }

    @Test
    void extendedDataAndLe_RoundTrips() {
        byte[] data = sequence(300);
        CommandApdu command = new CommandApdu(0x80, 0xE8, 0x80, 0x01, data, 65_536);

        byte[] encoded = command.toBytes();
        assertArrayEquals(hex("80 E8 80 01 00 01 2C"), Arrays.copyOf(encoded, 7));
        assertEquals(0x00, encoded[encoded.length - 2] & 0xFF);
        assertEquals(0x00, encoded[encoded.length - 1] & 0xFF);

        CommandApdu parsed = CommandApdu.fromBytes(encoded);
        assertArrayEquals(data, parsed.data());
        assertEquals(65_536, parsed.le());
    }

    @Test
    void rejectsMalformedShortAndExtendedStructures() {
        assertThrows(IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04")));
        assertThrows(IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04 00 02 01")));
        assertThrows(IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04 00 00 00")));
        assertThrows(IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04 00 00 00 02 01")));
        assertThrows(IllegalArgumentException.class, () -> new CommandApdu(0x00, 0xA4, 0x04, 0x00, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new CommandApdu(0x00, 0xA4, 0x04, 0x00, null, 65_537));
    }

    private static byte[] hex(String value) {
        return Util.parseHex(value);
    }

    private static byte[] sequence(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }
}
