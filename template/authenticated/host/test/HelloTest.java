package test;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.gp.scp.SecureChannelProfile;
import app.aoki.cinpo.gp.scp.SecureChannelSession;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import app.aoki.cinpo.task.Inject;
import java.util.Arrays;

@CardTaskDef(value = "test", order = 100)
public final class HelloTest implements CardTask {

    private static final int CLA_CINPO = 0x80;
    private static final int INS_ECHO = 0x01;
    private static final int INS_SHLO = 0x03;
    private static final byte[] ECHO_PAYLOAD = new byte[] {'p', 'i', 'n', 'g'};
    private static final byte[] SECURE_HELLO = new byte[] {'H', 'e', 'l', 'l', 'o', ',', ' ', 'S', 'C', 'P', '!'};

    @Inject
    private ApduChannel channel;

    @Inject
    private AppletManifest manifest;

    @Inject
    private SecureChannelProfile secureChannelProfile;

    public HelloTest() {
    }

    @Override
    public void run() {
        verifySecureHelloRejectedWithoutScp();
        try (SecureChannelSession session = SecureChannelSession.create(channel, secureChannelProfile)) {
            session.authenticate();
            if (!session.isAuthenticated()) {
                throw new IllegalStateException("Secure channel was not authenticated");
            }
            verifyEcho(session);
            verifySecureHello(session);
        }
    }

    private void verifyEcho(SecureChannelSession session) {
        ResponseApdu response = transmitOk(session, new CommandApdu(CLA_CINPO, INS_ECHO, 0x00, 0x00, ECHO_PAYLOAD, 256));
        if (!Arrays.equals(ECHO_PAYLOAD, response.data())) {
            throw new IllegalStateException("Echo response did not match request payload");
        }
    }

    private void verifySecureHello(SecureChannelSession session) {
        ResponseApdu response = transmitOk(session, new CommandApdu(CLA_CINPO, INS_SHLO, 0x00, 0x00, null, 256));
        if (!Arrays.equals(SECURE_HELLO, response.data())) {
            throw new IllegalStateException("Secure hello response did not match expected payload");
        }
    }

    private void verifySecureHelloRejectedWithoutScp() {
        ResponseApdu response = channel.transmit(new CommandApdu(CLA_CINPO, INS_SHLO, 0x00, 0x00, null, 256));
        if (response.sw() != 0x6982) {
            throw new IllegalStateException("Unauthenticated secure hello should fail with SW=6982 but got SW=%04X".formatted(response.sw()));
        }
    }

    private ResponseApdu transmitOk(SecureChannelSession session, CommandApdu command) {
        ResponseApdu response = session.transmit(command);
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
        }
        return response;
    }
}
