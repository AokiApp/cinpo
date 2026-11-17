package app.aoki.cinpo.gp.scp;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;

/**
 * Internal SCP strategy contract behind {@link SecureChannelSession}.
 */
interface InternalSecureChannelProtocol {

    void authenticate(ApduChannel channel);

    ResponseApdu transmit(ApduChannel channel, CommandApdu capdu);

    default void close() {
    }
}
