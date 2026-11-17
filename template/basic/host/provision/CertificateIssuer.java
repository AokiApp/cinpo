package provision;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.security.interfaces.RSAPublicKey;

/**
 * Issues an ephemeral, self-verifiable X.509 leaf certificate for the demo.
 *
 * <p>The certificate is created on the fly with the JDK's built-in crypto
 * primitives and a small in-tree DER encoder ({@link Der}). No third-party
 * cryptographic library is used. Both key pairs are discarded after issuing.
 */
final class CertificateIssuer {

    private static final String COMMON_NAME_OID = "2.5.4.3";
    private static final String ORGANIZATION_OID = "2.5.4.10";
    private static final String RSA_ENCRYPTION_ALGORITHM_OID = "1.2.840.113549.1.1.1";
    private static final String SHA256_WITH_RSA_ALGORITHM_OID = "1.2.840.113549.1.1.11";
    private static final String BASIC_CONSTRAINTS_OID = "2.5.29.19";

    private static final String ISSUER_COMMON_NAME = "CINPO Template Test CA";
    private static final String ISSUER_ORGANIZATION = "CINPO";
    private static final String SUBJECT_COMMON_NAME = "CINPO Template Leaf";
    private static final String SUBJECT_ORGANIZATION = "CINPO";

    private static final Instant NOT_BEFORE = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant NOT_AFTER = Instant.parse("2054-01-01T00:00:00Z");

    private CertificateIssuer() {
    }

    static byte[] issueLeafCertificate() {
        try {
            KeyPair caKeyPair = generateRsaKeyPair();
            KeyPair leafKeyPair = generateRsaKeyPair();
            byte[] certificate = buildVersion3Certificate(caKeyPair, leafKeyPair);
            selfCheck(certificate, caKeyPair);
            return certificate;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue CINPO template leaf certificate", e);
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static byte[] buildVersion3Certificate(KeyPair caKeyPair, KeyPair leafKeyPair) throws Exception {
        BigInteger serial = new BigInteger(128, new SecureRandom());
        byte[] signatureAlgorithm = Der.sequence(
                Der.objectIdentifier(SHA256_WITH_RSA_ALGORITHM_OID),
                Der.nullDER());
        byte[] tbs = Der.sequence(
                Der.explicit(0, Der.integer(BigInteger.valueOf(2))),
                Der.integer(serial),
                signatureAlgorithm,
                name(ISSUER_COMMON_NAME, ISSUER_ORGANIZATION),
                validity(),
                name(SUBJECT_COMMON_NAME, SUBJECT_ORGANIZATION),
                subjectPublicKeyInfo(leafKeyPair),
                extensions());
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(caKeyPair.getPrivate());
        signature.update(tbs);
        byte[] value = signature.sign();
        return Der.sequence(tbs, signatureAlgorithm, Der.bitString(value));
    }

    private static byte[] name(String commonName, String organization) {
        return Der.sequence(
                Der.set(attribute(COMMON_NAME_OID, Der.utf8String(commonName))),
                Der.set(attribute(ORGANIZATION_OID, Der.utf8String(organization))));
    }

    private static byte[] attribute(String typeOid, byte[] value) {
        return Der.sequence(Der.objectIdentifier(typeOid), value);
    }

    private static byte[] validity() {
        return Der.sequence(Der.generalizedTime(NOT_BEFORE), Der.generalizedTime(NOT_AFTER));
    }

    private static byte[] subjectPublicKeyInfo(KeyPair leafKeyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) leafKeyPair.getPublic();
        byte[] rsaPublicKey = Der.sequence(
                Der.integer(publicKey.getModulus()),
                Der.integer(publicKey.getPublicExponent()));
        return Der.sequence(
                Der.sequence(Der.objectIdentifier(RSA_ENCRYPTION_ALGORITHM_OID), Der.nullDER()),
                Der.bitString(rsaPublicKey));
    }

    /**
     * One v3 extension: basicConstraints with CA=FALSE, as expected for a leaf.
     */
    private static byte[] extensions() {
        byte[] basicConstraints = Der.sequence(
                Der.objectIdentifier(BASIC_CONSTRAINTS_OID),
                Der.octetString(Der.sequence()));
        return Der.explicit(3, Der.sequence(basicConstraints));
    }

    /**
     * Fails the build (and provisioning) if the JDK cannot read the certificate
     * we just produced. CertificateFactory is the cheapest available oracle.
     */
    private static void selfCheck(byte[] certificate, KeyPair caKeyPair) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate parsed = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificate));
        parsed.verify(caKeyPair.getPublic());
        parsed.checkValidity();
        String subject = parsed.getSubjectX500Principal().getName();
        if (!subject.contains("CN=CINPO Template Leaf")) {
            throw new IllegalStateException("Certificate self check failed: unexpected subject: " + subject);
        }
    }
}
