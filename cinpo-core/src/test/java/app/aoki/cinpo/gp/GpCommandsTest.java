package app.aoki.cinpo.gp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GpCommandsTest {

  /**
   * Builds an Initialize Update command with a fixed host challenge and compares the encoded bytes
   * with the expected APDU.
   *
   * <p>This checks the Amendment D command shape: CLA 0x80, INS 0x50, the supplied key version and
   * identifier, the eight-byte host challenge and the trailing Le byte.
   *
   * <p>Benefit: catches changes to the command layout before they reach a real card.
   */
  @DisplayName("Initialize Update encodes the Amendment D APDU layout.")
  @Test
  void encodesInitializeUpdateCommand() {
    CommandApdu command = GpCommands.initializeUpdate(0x10, 0x00, hex("01 02 03 04 05 06 07 08"));

    assertArrayEquals(hex("80 50 10 00 08 01 02 03 04 05 06 07 08 00"), command.toBytes());
  }

  /**
   * Builds an External Authenticate command with a 16-byte cryptogram-and-MAC field and checks the
   * encoded APDU.
   *
   * <p>This checks that the command uses the secure messaging CLA 0x84 and carries no Le byte, as
   * required for the authentication step of card opening.
   *
   * <p>Benefit: protects the CLA and Le choices that would otherwise make the card reject
   * authentication.
   */
  @DisplayName("External Authenticate uses the secure messaging CLA and no Le byte.")
  @Test
  void encodesExternalAuthenticateCommand() {
    CommandApdu command =
        GpCommands.externalAuthenticate(
            0x03, hex("00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"));

    assertArrayEquals(
        hex("84 82 03 00 10 00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"), command.toBytes());
  }

  /**
   * Builds an Install for Load command with a load file AID and no security domain AID, then checks
   * the encoded APDU.
   *
   * <p>This checks that the AIDs use short one-byte length fields, that the omitted fields are
   * encoded as zero-length fields and that P1 selects the load operation.
   *
   * <p>Benefit: pins the length encoding that the card parses when installing a load file.
   */
  @DisplayName("Install for Load encodes short AID lengths and empty optional fields.")
  @Test
  void encodesInstallForLoadCommand() {
    CommandApdu command = GpCommands.installForLoad(hex("A0 00 00 01 51"), null);

    assertEquals(GpCommands.INSTALL_P1_FOR_LOAD, command.p1());
    assertArrayEquals(hex("80 E6 02 00 0A 05 A0 00 00 01 51 00 00 00 00 00"), command.toBytes());
  }

  /**
   * Builds an Install for Install and Make Selectable command with a load file AID, an executable
   * module AID, an instance AID and a one-byte privileges field, then checks the encoded APDU.
   *
   * <p>This checks the P1 value for install and make selectable, the short length fields for the
   * three AIDs and the default two-byte C9 parameter block emitted when no install parameters are
   * supplied.
   *
   * <p>Benefit: a wrong length is caught here instead of making the card parse the applet fields
   * differently.
   */
  @DisplayName(
      "Install for Install and Make Selectable encodes the AID fields and default C9 parameters.")
  @Test
  void encodesInstallForInstallCommand() {
    CommandApdu command =
        GpCommands.installForInstallAndMakeSelectable(
            hex("A0 00 00 01 51"),
            hex("A0 00 00 01 51 01"),
            hex("A0 00 00 01 51 00 01"),
            hex("00"),
            null);

    assertEquals(GpCommands.INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE, command.p1());
    assertArrayEquals(
        hex(
            "80 E6 0C 00 1B 05 A0 00 00 01 51 06 A0 00 00 01 51 01 07 A0 00 00 01 51 00 01 01 00 02 C9 00 00 00"),
        command.toBytes());
  }

  /**
   * Builds a Delete command for an AID together with its related objects and checks the encoded
   * APDU.
   *
   * <p>This checks that the AID is wrapped in a 4F TLV and that P2 selects deletion of the object
   * and its related objects.
   *
   * <p>Benefit: keeps the delete scope explicit, so the command cannot change silently into a
   * single-object delete.
   */
  @DisplayName("Delete AID and Related encodes the AID TLV and the related-object P2 value.")
  @Test
  void encodesDeleteAidCommand() {
    CommandApdu command = GpCommands.deleteAidAndRelated(hex("A0 00 00 01 51"));

    assertEquals(GpCommands.DELETE_P2_DELETE_OBJECT_AND_RELATED, command.p2());
    assertArrayEquals(hex("80 E4 00 80 07 4F 05 A0 00 00 01 51 00"), command.toBytes());
  }

  /**
   * Builds a Load command for a final data block with a fixed block number and checks the encoded
   * APDU.
   *
   * <p>This checks that P1 carries the last-block flag, P2 carries the supplied block number and
   * the command ends with an Le byte.
   *
   * <p>Benefit: pins the block flags that tell the card how to finish loading the applet.
   */
  @DisplayName("Load encodes the block number, last-block flag and Le byte.")
  @Test
  void encodesLoadBlockCommand() {
    CommandApdu command = GpCommands.load(GpCommands.LOAD_P1_LAST_BLOCK, 0x7F, hex("C4 01 00"));

    assertArrayEquals(hex("80 E8 80 7F 03 C4 01 00 00"), command.toBytes());
  }

  /**
   * Calls each command builder with one invalid mandatory argument and expects an
   * IllegalArgumentException.
   *
   * <p>This checks that a short host challenge, an empty cryptogram, a short AID, a null AID and
   * null block data are rejected at the API boundary instead of being encoded into an APDU.
   *
   * <p>Benefit: invalid commands fail in the application rather than on a card, where the reason
   * would be harder to diagnose.
   */
  @DisplayName("Command builders reject invalid mandatory arguments.")
  @Test
  void rejectsInvalidMandatoryInputs() {
    assertThrows(
        IllegalArgumentException.class, () -> GpCommands.initializeUpdate(0, 0, hex("01 02 03")));
    assertThrows(
        IllegalArgumentException.class, () -> GpCommands.externalAuthenticate(0x01, new byte[0]));
    assertThrows(
        IllegalArgumentException.class, () -> GpCommands.installForLoad(hex("01 02 03 04"), null));
    assertThrows(IllegalArgumentException.class, () -> GpCommands.deleteAid(null));
    assertThrows(IllegalArgumentException.class, () -> GpCommands.load(0x00, 0x00, null));
  }

  private static byte[] hex(String value) {
    return Util.parseHex(value);
  }
}
