package test;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import app.aoki.cinpo.task.Inject;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

@CardTaskDef(value = "test", order = 100)
public final class HelloTest implements CardTask {

    private static final int CLA_CINPO = 0x80;
    private static final int INS_ECHO = 0x01;
    private static final int INS_GET_CERTIFICATE = 0x11;
    private static final int INS_GET_CERTIFICATE_LENGTH = 0x12;
    private static final byte[] ECHO_PAYLOAD = new byte[] {'p', 'i', 'n', 'g'};

    @Inject
    private ApduChannel channel;

    @Inject
    private AppletManifest manifest;

    public HelloTest() {
    }

    @Override
    public void run() {
        byte[] aid = manifest.applets().get(0).instanceAid();
        transmitOk(Iso7816Commands.selectDf(aid));
        verifyEcho();
        verifyWrittenCertificate();
    }

    private void verifyEcho() {
        ResponseApdu response = transmitOk(new CommandApdu(CLA_CINPO, INS_ECHO, 0x00, 0x00, ECHO_PAYLOAD, 256));
        if (!Arrays.equals(ECHO_PAYLOAD, response.data())) {
            throw new IllegalStateException("Echo response did not match request payload");
        }
    }

    private void verifyWrittenCertificate() {
        byte[] certificate = readCertificate();
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate parsed = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));
            String subject = parsed.getSubjectX500Principal().getName();
            if (!subject.contains("CN=CINPO Template Leaf")) {
                throw new IllegalStateException("Unexpected certificate subject: " + subject);
            }
            parsed.checkValidity();
        } catch (Exception e) {
            throw new IllegalStateException("Stored certificate is not a valid CINPO template leaf certificate", e);
        }
    }

    private byte[] readCertificate() {
        int length = readCertificateLength();
        byte[] certificate = new byte[length];
        int offset = 0;
        while (offset < length) {
            ResponseApdu response = transmitOk(new CommandApdu(
                    CLA_CINPO,
                    INS_GET_CERTIFICATE,
                    (offset >>> 8) & 0xFF,
                    offset & 0xFF,
                    null,
                    256
            ));
            byte[] chunk = response.data();
            if (chunk.length == 0) {
                throw new IllegalStateException("Certificate read returned an empty chunk at offset " + offset);
            }
            System.arraycopy(chunk, 0, certificate, offset, chunk.length);
            offset += chunk.length;
        }
        return certificate;
    }

    private int readCertificateLength() {
        ResponseApdu response = transmitOk(new CommandApdu(CLA_CINPO, INS_GET_CERTIFICATE_LENGTH, 0x00, 0x00, null, 2));
        byte[] data = response.data();
        if (data.length != 2) {
            throw new IllegalStateException("Certificate length response must be 2 bytes, got " + data.length);
        }
        int length = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        if (length <= 0) {
            throw new IllegalStateException("No certificate was written to the applet");
        }
        return length;
    }

    private ResponseApdu transmitOk(CommandApdu command) {
        ResponseApdu response = channel.transmit(command);
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
        }
        return response;
    }
}
