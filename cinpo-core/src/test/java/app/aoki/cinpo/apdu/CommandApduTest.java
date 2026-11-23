package app.aoki.cinpo.apdu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.util.Util;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandApduTest {

  /**
   * Encodes a case 1 header-only command APDU and parses the bytes back.
   *
   * <p>Verifies that no data or Le is reported, the bytes are exactly 00 A4 04 00, and parsing
   * recovers the four header fields. This is the simplest ISO 7816 encoding, so it anchors the
   * codec before more complex cases are considered.
   */
  @DisplayName("A case 1 header-only command APDU round-trips through encoding and parsing")
  @Test
  void roundTripsHeaderOnly() {
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

  /**
   * Encodes a short Le of 256 and parses the resulting command APDU.
   *
   * <p>Verifies that Le is encoded as the single zero byte required by the short form and that
   * parsing restores the value 256. This covers the boundary where 256 cannot be written literally
   * in one byte and must use the zero-encoding convention.
   */
  @DisplayName("A short Le of 256 is encoded as the zero byte")
  @Test
  void encodesShortLe256AsZero() {
    CommandApdu command = new CommandApdu(0x80, 0x50, 0x00, 0x00, null, 256);

    assertTrue(command.hasLe());
    assertFalse(command.isExtendedLength());
    assertArrayEquals(hex("80 50 00 00 00"), command.toBytes());

    CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
    assertEquals(256, parsed.le());
  }

  /**
   * Encodes a case 3 command with short data after mutating the source array, then parses it.
   *
   * <p>Verifies that the encoded bytes keep the original data, that the accessor returns a
   * defensive copy, and that parsing recovers the same data. This prevents callers from corrupting
   * a command by reusing its input or returned arrays.
   */
  @DisplayName("A case 3 command with short data round-trips and defensively copies its bytes")
  @Test
  void copiesShortDataDefensively() {
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

  /**
   * Encodes a case 4 command with short data and Le, then parses the bytes back.
   *
   * <p>Verifies that Lc, data, and the zero byte for Le are laid out as expected and that parsing
   * restores both the data and Le 256. This covers the case that combines command data with an
   * expected response length.
   */
  @DisplayName("A case 4 command with short data and Le round-trips through encoding and parsing")
  @Test
  void roundTripsDataAndLe() {
    CommandApdu command = new CommandApdu(0x00, 0xCA, 0x01, 0x02, hex("5C 02 7F 21"), 256);

    assertArrayEquals(hex("00 CA 01 02 04 5C 02 7F 21 00"), command.toBytes());

    CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
    assertEquals(256, parsed.le());
    assertArrayEquals(hex("5C 02 7F 21"), parsed.data());
  }

  /**
   * Encodes 256 bytes of extended-length data and parses the resulting command APDU.
   *
   * <p>Verifies that the extended marker, three-byte Lc, and total length are correct and that
   * parsing recovers every data byte with no Le. This checks the transition from short to extended
   * form at the 256-byte boundary.
   */
  @DisplayName("Extended-length data uses a three-byte Lc and round-trips")
  @Test
  void encodesExtendedDataWithLc() {
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

  /**
   * Encodes an extended Le of 65536 and parses the resulting command APDU.
   *
   * <p>Verifies that the extended marker and the three zero bytes for Le are produced and that
   * parsing restores 65536. This is the boundary where Le must use the extended zero encoding
   * rather than a literal value.
   */
  @DisplayName("An extended Le of 65536 is encoded with the zero extended-Le value")
  @Test
  void encodesExtendedLeAsZero() {
    CommandApdu command = new CommandApdu(0x00, 0xCA, 0x00, 0x00, null, 65_536);

    assertTrue(command.isExtendedLength());
    assertArrayEquals(hex("00 CA 00 00 00 00 00"), command.toBytes());

    CommandApdu parsed = CommandApdu.fromBytes(command.toBytes());
    assertEquals(65_536, parsed.le());
  }

  /**
   * Encodes 300 bytes of extended data together with an extended Le of 65536, then parses it.
   *
   * <p>Verifies the extended header, the trailing zero bytes for Le, and the recovery of both data
   * and Le. This is the longest form the codec supports, so it catches off-by-one errors in the
   * extended-length header.
   */
  @DisplayName("A command with extended data and Le round-trips through encoding and parsing")
  @Test
  void roundTripsExtendedFields() {
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

  /**
   * Builds malformed short and extended APDU byte sequences and invalid Le values.
   *
   * <p>Verifies that parsing short inputs, inconsistent lengths, and out-of-range Le values all
   * throw IllegalArgumentException. This keeps invalid input from being interpreted as a valid
   * command and sent to a card.
   */
  @DisplayName("Malformed short and extended APDU structures are rejected")
  @Test
  void rejectsMalformedStructures() {
    assertThrows(IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04")));
    assertThrows(
        IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04 00 02 01")));
    assertThrows(
        IllegalArgumentException.class, () -> CommandApdu.fromBytes(hex("00 A4 04 00 00 00")));
    assertThrows(
        IllegalArgumentException.class,
        () -> CommandApdu.fromBytes(hex("00 A4 04 00 00 00 02 01")));
    assertThrows(
        IllegalArgumentException.class, () -> new CommandApdu(0x00, 0xA4, 0x04, 0x00, null, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CommandApdu(0x00, 0xA4, 0x04, 0x00, null, 65_537));
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
