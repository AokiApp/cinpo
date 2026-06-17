package com.example.cinpo.hello;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;

public final class HelloApplet extends Applet implements ExtendedLength {

    private static final byte CLA_CINPO = (byte) 0x80;
    private static final byte INS_ECHO = (byte) 0x01;
    private static final byte INS_HELLO = (byte) 0x02;
    private static final byte INS_PUT_CERTIFICATE = (byte) 0x10;
    private static final byte INS_GET_CERTIFICATE = (byte) 0x11;
    private static final byte INS_GET_CERTIFICATE_LENGTH = (byte) 0x12;
    private static final short MAX_CERTIFICATE_LENGTH = (short) 2048;
    private static final short MAX_RESPONSE_CHUNK = (short) 240;
    private static final byte[] HELLO = new byte[] {
            'H', 'e', 'l', 'l', 'o', ',', ' ', 'C', 'I', 'N', 'P', 'O', '!'
    };

    private final byte[] certificate;
    private short certificateLength;

    private HelloApplet() {
        certificate = new byte[MAX_CERTIFICATE_LENGTH];
        certificateLength = (short) 0;
    }

    public static void install(byte[] buffer, short offset, byte length) {
        byte aidLength = buffer[offset];
        new HelloApplet().register(buffer, (short) (offset + 1), aidLength);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_CLA] != CLA_CINPO) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_ECHO:
                echo(apdu, buffer);
                return;
            case INS_HELLO:
                hello(apdu, buffer);
                return;
            case INS_PUT_CERTIFICATE:
                putCertificate(apdu, buffer);
                return;
            case INS_GET_CERTIFICATE:
                getCertificate(apdu, buffer);
                return;
            case INS_GET_CERTIFICATE_LENGTH:
                getCertificateLength(apdu, buffer);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private static void echo(APDU apdu, byte[] buffer) {
        short length = apdu.setIncomingAndReceive();
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
    }

    private static void hello(APDU apdu, byte[] buffer) {
        short length = (short) HELLO.length;
        Util.arrayCopyNonAtomic(HELLO, (short) 0, buffer, (short) 0, length);
        apdu.setOutgoingAndSend((short) 0, length);
    }

    private void putCertificate(APDU apdu, byte[] buffer) {
        short offset = readOffset(buffer);
        short received = apdu.setIncomingAndReceive();
        if ((short) (offset + received) > MAX_CERTIFICATE_LENGTH) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        if (offset == 0) {
            certificateLength = 0;
        }
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, certificate, offset, received);
        short endOffset = (short) (offset + received);
        if (endOffset > certificateLength) {
            certificateLength = endOffset;
        }
    }

    private void getCertificate(APDU apdu, byte[] buffer) {
        short offset = readOffset(buffer);
        if (offset > certificateLength) {
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }
        short available = (short) (certificateLength - offset);
        short length = available > MAX_RESPONSE_CHUNK ? MAX_RESPONSE_CHUNK : available;
        Util.arrayCopyNonAtomic(certificate, offset, buffer, (short) 0, length);
        apdu.setOutgoingAndSend((short) 0, length);
    }

    private void getCertificateLength(APDU apdu, byte[] buffer) {
        buffer[0] = (byte) (certificateLength >> 8);
        buffer[1] = (byte) certificateLength;
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private static short readOffset(byte[] buffer) {
        return (short) (((buffer[ISO7816.OFFSET_P1] & 0xFF) << 8) | (buffer[ISO7816.OFFSET_P2] & 0xFF));
    }
}
