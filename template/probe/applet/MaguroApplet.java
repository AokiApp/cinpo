package com.example.cinpo.probe;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

public final class MaguroApplet extends Applet {

    private MaguroApplet() {
    }

    public static void install(byte[] buffer, short offset, byte length) {
        byte aidLength = buffer[offset];
        new MaguroApplet().register(buffer, (short) (offset + 1), aidLength);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
}
