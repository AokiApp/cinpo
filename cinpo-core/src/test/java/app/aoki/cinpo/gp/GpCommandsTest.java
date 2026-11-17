package app.aoki.cinpo.gp;

import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpCommandsTest {

    @Test
    void initializeUpdate_EncodesAmdDCommandShape() {
        CommandApdu command = GpCommands.initializeUpdate(0x10, 0x00, hex("01 02 03 04 05 06 07 08"));

        assertArrayEquals(hex("80 50 10 00 08 01 02 03 04 05 06 07 08 00"), command.toBytes());
    }

    @Test
    void externalAuthenticate_UsesSecureMessagingClaAndNoLe() {
        CommandApdu command = GpCommands.externalAuthenticate(0x03, hex("00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"));

        assertArrayEquals(hex("84 82 03 00 10 00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"), command.toBytes());
    }

    @Test
    void installForLoad_EncodesShortLvAndBerLvFields() {
        CommandApdu command = GpCommands.installForLoad(hex("A0 00 00 01 51"), null);

        assertEquals(GpCommands.INSTALL_P1_FOR_LOAD, command.p1());
        assertArrayEquals(hex("80 E6 02 00 0A 05 A0 00 00 01 51 00 00 00 00 00"), command.toBytes());
    }

    @Test
    void installForInstallAndMakeSelectable_EncodesApplicationFieldsAndDefaultC9Parameters() {
        CommandApdu command = GpCommands.installForInstallAndMakeSelectable(
                hex("A0 00 00 01 51"),
                hex("A0 00 00 01 51 01"),
                hex("A0 00 00 01 51 00 01"),
                hex("00"),
                null
        );

        assertEquals(GpCommands.INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE, command.p1());
        assertArrayEquals(hex("80 E6 0C 00 1B 05 A0 00 00 01 51 06 A0 00 00 01 51 01 07 A0 00 00 01 51 00 01 01 00 02 C9 00 00 00"), command.toBytes());
    }

    @Test
    void deleteAidAndRelated_EncodesAidTlvAndRelatedP2() {
        CommandApdu command = GpCommands.deleteAidAndRelated(hex("A0 00 00 01 51"));

        assertEquals(GpCommands.DELETE_P2_DELETE_OBJECT_AND_RELATED, command.p2());
        assertArrayEquals(hex("80 E4 00 80 07 4F 05 A0 00 00 01 51 00"), command.toBytes());
    }

    @Test
    void load_EncodesBlockNumberAndLe() {
        CommandApdu command = GpCommands.load(GpCommands.LOAD_P1_LAST_BLOCK, 0x7F, hex("C4 01 00"));

        assertArrayEquals(hex("80 E8 80 7F 03 C4 01 00 00"), command.toBytes());
    }

    @Test
    void rejectsInvalidMandatoryInputs() {
        assertThrows(IllegalArgumentException.class, () -> GpCommands.initializeUpdate(0, 0, hex("01 02 03")));
        assertThrows(IllegalArgumentException.class, () -> GpCommands.externalAuthenticate(0x01, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> GpCommands.installForLoad(hex("01 02 03 04"), null));
        assertThrows(IllegalArgumentException.class, () -> GpCommands.deleteAid(null));
        assertThrows(IllegalArgumentException.class, () -> GpCommands.load(0x00, 0x00, null));
    }

    private static byte[] hex(String value) {
        return Util.parseHex(value);
    }
}
