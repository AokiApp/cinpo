package provision;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import app.aoki.cinpo.task.Inject;
import java.nio.charset.StandardCharsets;

@CardTaskDef(value = "provision", order = 100)
public final class HelloProvision implements CardTask {

    private static final int CLA_CINPO = 0x80;
    private static final int INS_HELLO = 0x02;
    private static final int GP_APPLICATION_SELECTABLE = 0x07;

    @Inject
    private ApduChannel channel;

    @Inject
    private AppletManifest manifest;

    public HelloProvision() {
    }

    @Override
    public void run() {
        byte[] aid = manifest.applets().get(0).instanceAid();
        transmitOk(Iso7816Commands.selectDf(aid));
        verifyHello();
    }

    private void verifyHello() {
        ResponseApdu response = transmitOk(new CommandApdu(CLA_CINPO, INS_HELLO, 0x00, 0x00, null, 256));
        byte[] data = response.data();
        byte[] expectedMessage = "Hello, CINPO!".getBytes(StandardCharsets.US_ASCII);
        if (data.length != expectedMessage.length + 1) {
            throw new IllegalStateException("Unexpected hello response length: " + data.length);
        }
        String message = new String(data, 0, expectedMessage.length, StandardCharsets.US_ASCII);
        if (!"Hello, CINPO!".equals(message)) {
            throw new IllegalStateException("Unexpected hello response message: " + message);
        }
        int contentState = data[expectedMessage.length] & 0xFF;
        if (contentState != GP_APPLICATION_SELECTABLE) {
            throw new IllegalStateException("Unexpected GP content state: 0x%02X".formatted(contentState));
        }
    }

    private ResponseApdu transmitOk(CommandApdu command) {
        ResponseApdu response = channel.transmit(command);
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
        }
        return response;
    }
}
