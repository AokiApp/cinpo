package provision;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.security.Security;

final class CertificateIssuer {

    private static final String PROVIDER = "BC";

    private CertificateIssuer() {
    }

    static byte[] issueLeafCertificate() {
        try {
            ensureProvider();
            KeyPair caKeyPair = generateRsaKeyPair();
            KeyPair leafKeyPair = generateRsaKeyPair();
            X500Name issuer = new X500Name("CN=CINPO Template Test CA,O=CINPO");
            X500Name subject = new X500Name("CN=CINPO Template Leaf,O=CINPO");
            Date notBefore = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
            Date notAfter = Date.from(Instant.parse("2034-01-01T00:00:00Z"));
            BigInteger serial = new BigInteger(128, new SecureRandom());
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer,
                    serial,
                    notBefore,
                    notAfter,
                    subject,
                    leafKeyPair.getPublic()
            );
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(PROVIDER)
                    .build(caKeyPair.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider(PROVIDER)
                    .getCertificate(builder.build(signer));
            certificate.verify(caKeyPair.getPublic());
            return certificate.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue CINPO template leaf certificate", e);
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void ensureProvider() {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
