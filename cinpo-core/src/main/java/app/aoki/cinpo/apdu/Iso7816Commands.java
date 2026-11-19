package app.aoki.cinpo.apdu;

import app.aoki.cinpo.util.Util;

import java.util.Arrays;

/**
 * Frequently used ISO 7816 command templates that build {@link CommandApdu}
 * values without embedding response semantics.
 */
public final class Iso7816Commands {

    public static final int INS_VERIFY = 0x20;
    public static final int INS_CHANGE_REFERENCE_DATA = 0x24;
    public static final int INS_GET_CHALLENGE = 0x84;
    public static final int INS_SELECT = 0xA4;
    public static final int INS_READ_BINARY = 0xB0;
    public static final int INS_GET_DATA = 0xCA;
    public static final int INS_PUT_DATA = 0xDA;
    public static final int INS_UPDATE_BINARY = 0xD6;

    public static final int SELECT_METHOD_BY_DF_NAME = 0x04;
    public static final int SELECT_METHOD_EF_UNDER_CURRENT_DF = 0x02;
    public static final int SELECT_OPTION_FIRST_OR_ONLY_OCCURRENCE = 0x00;
    public static final int SELECT_OPTION_RETURN_FCI_TEMPLATE = 0x04;
    public static final int SELECT_OPTION_RETURN_NO_DATA = 0x0C;

    private static final int MAX_AID_LENGTH = 16;
    private static final int MAX_FILE_ID = 0xFFFF;
    private static final int MAX_BINARY_OFFSET = 0x7FFF;
    private static final int MAX_SFI = 0x1F;
    private static final int MAX_SFI_BINARY_OFFSET = 0xFF;
    private static final int MIN_ONE_BYTE_BER_TLV_TAG = 0x40;
    private static final int MAX_ONE_BYTE_BER_TLV_TAG = 0xFE;
    private static final int MIN_TWO_BYTE_BER_TLV_TAG = 0x4001;
    private static final int MAX_TWO_BYTE_BER_TLV_TAG = 0xFFFE;

    private Iso7816Commands() {
    }

    public static CommandApdu selectDf(byte[] dfName) {
        return selectDf(dfName, SELECT_OPTION_RETURN_NO_DATA, null);
    }

    public static CommandApdu selectDf(byte[] dfName, int selectOptions) {
        return selectDf(dfName, selectOptions, null);
    }

    public static CommandApdu selectDf(byte[] dfName, int selectOptions, Integer le) {
        return new CommandApdu(
                0x00,
                INS_SELECT,
                SELECT_METHOD_BY_DF_NAME,
                Util.requireByte(selectOptions),
                requireAid(dfName),
                le);
    }

    public static CommandApdu selectEf(int fileId) {
        return selectEf(fileId, false, null);
    }

    public static CommandApdu selectEf(int fileId, boolean requestFci) {
        return selectEf(fileId, requestFci, null);
    }

    public static CommandApdu selectEf(int fileId, boolean requestFci, Integer le) {
        int selectOptions = requestFci ? SELECT_OPTION_RETURN_FCI_TEMPLATE : SELECT_OPTION_RETURN_NO_DATA;
        return new CommandApdu(
                0x00,
                INS_SELECT,
                SELECT_METHOD_EF_UNDER_CURRENT_DF,
                selectOptions,
                encodeFileId(fileId),
                le);
    }

    public static CommandApdu readBinary(int offset, int le) {
        requireBinaryOffset(offset);
        return new CommandApdu(
                0x00,
                INS_READ_BINARY,
                (offset >>> 8) & 0x7F,
                offset & 0xFF,
                null,
                le);
    }

    public static CommandApdu readBinaryBySfi(int sfi, int offset, int le) {
        requireSfi(sfi);
        requireSfiBinaryOffset(offset);
        return new CommandApdu(
                0x00,
                INS_READ_BINARY,
                0x80 | (sfi & MAX_SFI),
                offset,
                null,
                le);
    }

    public static CommandApdu updateBinary(int offset, byte[] data) {
        requireBinaryOffset(offset);
        return new CommandApdu(
                0x00,
                INS_UPDATE_BINARY,
                (offset >>> 8) & 0x7F,
                offset & 0xFF,
                requireData(data));
    }

    public static CommandApdu updateBinaryBySfi(int sfi, int offset, byte[] data) {
        requireSfi(sfi);
        requireSfiBinaryOffset(offset);
        return new CommandApdu(
                0x00,
                INS_UPDATE_BINARY,
                0x80 | (sfi & MAX_SFI),
                offset,
                requireData(data));
    }

    public static CommandApdu verify(int referenceDataNumber, byte[] verificationData) {
        return new CommandApdu(
                0x00,
                INS_VERIFY,
                0x00,
                0x80 | Util.requireByte(referenceDataNumber),
                requireData(verificationData));
    }

    public static CommandApdu changeReferenceData(int referenceDataNumber, byte[] newReferenceData) {
        return new CommandApdu(
                0x00,
                INS_CHANGE_REFERENCE_DATA,
                0x00,
                0x80 | Util.requireByte(referenceDataNumber),
                requireData(newReferenceData));
    }

    public static CommandApdu getChallenge(int le) {
        return new CommandApdu(
                0x00,
                INS_GET_CHALLENGE,
                0x00,
                0x00,
                null,
                le);
    }

    public static CommandApdu getData(int tag) {
        return getData(tag, null);
    }

    public static CommandApdu getData(int tag, Integer le) {
        int[] referenceControl = encodeDataObjectReferenceControl(tag);
        return new CommandApdu(
                0x00,
                INS_GET_DATA,
                referenceControl[0],
                referenceControl[1],
                null,
                le);
    }

    public static CommandApdu putData(int tag, byte[] value) {
        int[] referenceControl = encodeDataObjectReferenceControl(tag);
        return new CommandApdu(
                0x00,
                INS_PUT_DATA,
                referenceControl[0],
                referenceControl[1],
                requireData(value));
    }

    private static byte[] requireAid(byte[] aid) {
        if (aid == null || aid.length == 0) {
            throw new IllegalArgumentException("AID must not be null or empty");
        }
        if (aid.length > MAX_AID_LENGTH) {
            throw new IllegalArgumentException("AID must be between 1 and 16 bytes");
        }
        return Arrays.copyOf(aid, aid.length);
    }

    private static byte[] requireData(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Command data must not be null or empty");
        }
        return Arrays.copyOf(data, data.length);
    }

    private static byte[] encodeFileId(int fileId) {
        if (fileId < 0 || fileId > MAX_FILE_ID) {
            throw new IllegalArgumentException("fileId must be between 0x0000 and 0xFFFF");
        }
        return new byte[]{
            (byte) ((fileId >>> 8) & 0xFF),
            (byte) (fileId & 0xFF)
        };
    }

    private static void requireBinaryOffset(int offset) {
        if (offset < 0 || offset > MAX_BINARY_OFFSET) {
            throw new IllegalArgumentException("Binary offset must be between 0 and 32767");
        }
    }

    private static void requireSfi(int sfi) {
        if (sfi < 0 || sfi > MAX_SFI) {
            throw new IllegalArgumentException("SFI must be between 0 and 31");
        }
    }

    private static void requireSfiBinaryOffset(int offset) {
        if (offset < 0 || offset > MAX_SFI_BINARY_OFFSET) {
            throw new IllegalArgumentException("SFI binary offset must be between 0 and 255");
        }
    }

    private static int[] encodeDataObjectReferenceControl(int tag) {
        if (tag >= MIN_ONE_BYTE_BER_TLV_TAG && tag <= MAX_ONE_BYTE_BER_TLV_TAG) {
            return new int[]{0x00, tag};
        }
        if (tag >= MIN_TWO_BYTE_BER_TLV_TAG && tag <= MAX_TWO_BYTE_BER_TLV_TAG && tag != 0xFFFF) {
            return new int[]{(tag >>> 8) & 0xFF, tag & 0xFF};
        }
        throw new IllegalArgumentException(
                "BER-TLV tag must be in 0x0040..0x00FE or 0x4001..0xFFFE for GET/PUT DATA reference control");
    }

}
