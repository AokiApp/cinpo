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
import java.util.Arrays;

@CardTaskDef(value = "provision", order = 100)
public final class HelloProvision implements CardTask {

    private static final int CLA_CINPO = 0x80;
    private static final int INS_HELLO = 0x02;
    private static final int INS_PUT_CERTIFICATE = 0x10;
    private static final int CERTIFICATE_CHUNK_SIZE = 220;

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
        byte[] leafCertificate = CertificateIssuer.issueLeafCertificate();
        writeCertificate(leafCertificate);
    }

    private void verifyHello() {
        ResponseApdu response = transmitOk(new CommandApdu(CLA_CINPO, INS_HELLO, 0x00, 0x00, null, 256));
        String message = new String(response.data(), StandardCharsets.US_ASCII);
        if (!"Hello, CINPO!".equals(message)) {
            throw new IllegalStateException("Unexpected hello response: " + message);
        }
    }

    private void writeCertificate(byte[] certificate) {
        for (int offset = 0; offset < certificate.length; offset += CERTIFICATE_CHUNK_SIZE) {
            int length = Math.min(CERTIFICATE_CHUNK_SIZE, certificate.length - offset);
            byte[] chunk = Arrays.copyOfRange(certificate, offset, offset + length);
            transmitOk(new CommandApdu(
                    CLA_CINPO,
                    INS_PUT_CERTIFICATE,
                    (offset >>> 8) & 0xFF,
                    offset & 0xFF,
                    chunk
            ));
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
