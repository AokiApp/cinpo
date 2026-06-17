package com.example.cinpo.hello;

import org.globalplatform.GPSystem;
import org.globalplatform.SecureChannel;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

public final class HelloApplet extends Applet {

    private static final byte CLA_CINPO = (byte) 0x80;
    private static final byte INS_ECHO = (byte) 0x01;
    private static final byte INS_HELLO = (byte) 0x02;
    private static final byte INS_SHLO = (byte) 0x03;
    private static final byte[] HELLO = new byte[] {
            'H', 'e', 'l', 'l', 'o', ',', ' ', 'C', 'I', 'N', 'P', 'O', '!'
    };
    private static final byte[] SECURE_HELLO = new byte[] {
            'H', 'e', 'l', 'l', 'o', ',', ' ', 'S', 'C', 'P', '!'
    };

    private HelloApplet() {
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
        SecureChannel secureChannel = GPSystem.getSecureChannel();
        if (isSecurityProtocolCommand(buffer)) {
            short length = secureChannel.processSecurity(apdu);
            apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
            return;
        }

        boolean secureMessaging = isSecureMessagingCommand(buffer);
        if (secureMessaging) {
            short received = apdu.setIncomingAndReceive();
            secureChannel.unwrap(buffer, (short) 0, (short) (ISO7816.OFFSET_CDATA + received));
        }

        if (buffer[ISO7816.OFFSET_CLA] != CLA_CINPO) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_ECHO:
                echo(apdu, buffer, secureChannel, secureMessaging);
                return;
            case INS_HELLO:
                hello(apdu, buffer);
                return;
            case INS_SHLO:
                secureHello(apdu, buffer, secureChannel, secureMessaging);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private static void echo(APDU apdu, byte[] buffer, SecureChannel secureChannel, boolean secureMessaging) {
        short length = secureMessaging ? buffer[ISO7816.OFFSET_LC] : apdu.setIncomingAndReceive();
        if (!secureMessaging) {
            apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
            return;
        }

        sendSecureResponse(apdu, buffer, secureChannel, ISO7816.OFFSET_CDATA, length);
    }

    private static boolean isSecurityProtocolCommand(byte[] buffer) {
        byte ins = buffer[ISO7816.OFFSET_INS];
        return ins == (byte) 0x50 || ins == (byte) 0x82;
    }

    private static boolean isSecureMessagingCommand(byte[] buffer) {
        byte cla = buffer[ISO7816.OFFSET_CLA];
        return (cla & (byte) 0x04) != 0 || (cla & (byte) 0x20) != 0;
    }

    private static void hello(APDU apdu, byte[] buffer) {
        short length = (short) HELLO.length;
        Util.arrayCopyNonAtomic(HELLO, (short) 0, buffer, (short) 0, length);
        buffer[length] = GPSystem.getCardContentState();
        apdu.setOutgoingAndSend((short) 0, (short) (length + 1));
    }

    private static void secureHello(APDU apdu, byte[] buffer, SecureChannel secureChannel, boolean secureMessaging) {
        if ((secureChannel.getSecurityLevel() & SecureChannel.AUTHENTICATED) == 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        short length = (short) SECURE_HELLO.length;
        Util.arrayCopyNonAtomic(SECURE_HELLO, (short) 0, buffer, ISO7816.OFFSET_CDATA, length);
        if (secureMessaging) {
            sendSecureResponse(apdu, buffer, secureChannel, ISO7816.OFFSET_CDATA, length);
            return;
        }
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
    }

    private static void sendSecureResponse(APDU apdu, byte[] buffer, SecureChannel secureChannel, short offset, short length) {
        buffer[(short) (offset + length)] = (byte) 0x90;
        buffer[(short) (offset + length + 1)] = (byte) 0x00;
        short wrappedLength = secureChannel.wrap(buffer, offset, (short) (length + 2));
        apdu.setOutgoingAndSend(offset, wrappedLength);
    }
}
