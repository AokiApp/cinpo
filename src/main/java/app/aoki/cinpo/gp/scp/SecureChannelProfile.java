package app.aoki.cinpo.gp.scp;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.util.Util;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable configuration input for establishing a GlobalPlatform Secure Channel Session.
 *
 * <p>A {@code SecureChannelProfile} bundles all static parameters required before the
 * on-card Security Domain can be contacted: key material, key set addressing, desired
 * security level, the SCP variant, and the host challenge. It is consumed by
 * {@link SecureChannelSession#create(ApduChannel, SecureChannelProfile)} to create a
 * session object, and is never mutated after construction.
 *
 * <h2>Supported Protocols</h2>
 * <ul>
 *   <li><b>SCP03</b> (recommended) – AES-based Secure Channel Protocol as defined in
 *       [Amd D]. See {@link #scp03}.</li>
 *   <li><b>SCP02</b> (deprecated) – DES-based Secure Channel Protocol. Its SCP
 *       identifier {@code '02'} is marked "Deprecated, reserved" in [GPCS] §10.7.
 *       See {@link #scp02}.</li>
 * </ul>
 *
 * <h2>Key Requirements (SCP03)</h2>
 * A Security Domain supporting SCP03 shall have at least one complete key set containing
 * a Key-ENC, a Key-MAC, and a Key-DEK of the same length ([Amd D] §6.1).  Each key must
 * be 16, 24, or 32 bytes (AES-128/192/256).
 *
 * @param scp                 Secure Channel Protocol variant that this profile is valid for.
 * @param securityDomainAid   AID of the Security Domain to SELECT before the
 *                            INITIALIZE UPDATE command is issued. Must not be null or empty.
 * @param keyVersionNumber    Key Version Number used to address the key set within the
 *                            Security Domain (0x00–0xFF). A value of {@code 0x00} instructs
 *                            the card to use the first available key set it finds
 *                            ([Amd D] §7.1.1.3).
 * @param keyIdentifier       Key Identifier within the addressed key set (0x00–0xFF).
 *                            <b>For SCP03 this must always be {@code 0x00}.</b>  The
 *                            INITIALIZE UPDATE Reference Control Parameter P2 "shall always
 *                            be set to '00'" ([Amd D] §7.1.1.4). Passing a non-zero value
 *                            for SCP03 violates the specification.
 * @param securityLevel       Requested session security level, encoded as a bitmap per
 *                            [Amd D] §7.1.2.3 Table 7-6. Valid combinations:
 *                            <ul>
 *                              <li>{@code 0x00} – no secure messaging</li>
 *                              <li>{@code 0x01} – C-MAC</li>
 *                              <li>{@code 0x03} – C-DECRYPTION and C-MAC</li>
 *                              <li>{@code 0x11} – C-MAC and R-MAC</li>
 *                              <li>{@code 0x13} – C-DECRYPTION, C-MAC, and R-MAC</li>
 *                              <li>{@code 0x33} – C-DECRYPTION, R-ENCRYPTION, C-MAC, and R-MAC</li>
 *                            </ul>
 *                            The EXTERNAL AUTHENTICATE command conveys this value as P1
 *                            and the card enforces it for all subsequent secure messages.
 * @param encKey              Static Secure Channel Encryption Key (Key-ENC). Used to
 *                            derive the session S-ENC key for data decryption/encryption.
 *                            Must be 16, 24, or 32 bytes ([Amd D] Table 6-1).
 * @param macKey              Static Secure Channel MAC Key (Key-MAC). Used to derive the
 *                            session S-MAC and S-RMAC keys for integrity and data origin
 *                            authentication. Must match the length of {@code encKey}
 *                            ([Amd D] Table 6-1).
 * @param dekKey              Data Encryption Key (Key-DEK). Used for sensitive data
 *                            confidentiality (e.g. key loading). Must match the length of
 *                            {@code encKey} when provided. May be {@code null} when DEK
 *                            operations are not required ([Amd D] Table 6-1).
 * @param hostChallenge       8-byte host challenge included in the INITIALIZE UPDATE
 *                            command data field. Per [Amd D] §7.1.1.5, "this challenge,
 *                            chosen by the off-card entity, should be unique to this
 *                            session." If {@code null}, a cryptographically random 8-byte
 *                            value is generated automatically via {@link java.security.SecureRandom}.
 * @param scp02SequenceCounter 2-byte sequence counter required only for SCP02 implicit
 *                             initiation ([GPCS] §10.2.2). Should be {@code null} for SCP03
 *                             or for SCP02 explicit initiation. Must be exactly 2 bytes
 *                             when provided.
 */
public record SecureChannelProfile(
        Scp scp,
        byte[] securityDomainAid,
        int keyVersionNumber,
        int keyIdentifier,
        int securityLevel,
        byte[] encKey,
        byte[] macKey,
        byte[] dekKey,
        byte[] hostChallenge,
        byte[] scp02SequenceCounter) {

    public enum Scp {
        SCP02,
        SCP03
    }

    public SecureChannelProfile {
        Objects.requireNonNull(scp);
        securityDomainAid = copyRequired(securityDomainAid);
        keyVersionNumber = Util.requireByte(keyVersionNumber);
        keyIdentifier = Util.requireByte(keyIdentifier);
        securityLevel = Util.requireByte(securityLevel);
        encKey = copyKey(encKey);
        macKey = copyKey(macKey);
        dekKey = dekKey == null ? null : copyKey(dekKey);
        hostChallenge = normalizeHostChallenge(hostChallenge);
        scp02SequenceCounter = normalizeScp02SequenceCounter(scp02SequenceCounter);
        requireCompatibleKeyLengths(encKey, macKey, dekKey);
    }

    /**
     * Creates a configuration profile for SCP03 (AES-based Secure Channel Protocol).
     *
     * <p>SCP03 is the recommended protocol for new integrations. It is based on AES
     * cryptography and "protects bidirectional communication between the Host and the
     * card (decryption/MAC verification for incoming commands, encryption/MAC generation
     * on card response)" ([GPCS] §10.7).
     *
     * <p>The {@code hostChallenge} is generated automatically using
     * {@link java.security.SecureRandom} when this factory is used. Use the full
     * {@link #SecureChannelProfile} constructor directly if you need to supply a
     * deterministic challenge.
     *
     * <p><b>Note:</b> {@code keyIdentifier} must be {@code 0x00} for SCP03 per
     * [Amd D] §7.1.1.4. Passing any other value violates the specification.
     *
     * @param securityDomainAid AID of the Security Domain to select. Must not be null or empty.
     * @param keyVersionNumber  Key Version Number (0x00–0xFF); {@code 0x00} = card selects first
     *                          available key set ([Amd D] §7.1.1.3).
     * @param keyIdentifier     Must be {@code 0x00} for SCP03 ([Amd D] §7.1.1.4).
     * @param securityLevel     Session security level bitmap per [Amd D] Table 7-6
     *                          (e.g. {@code 0x03} for C-DECRYPTION+C-MAC).
     * @param encKey            Static Key-ENC, 16/24/32 bytes ([Amd D] Table 6-1).
     * @param macKey            Static Key-MAC, same length as {@code encKey} ([Amd D] Table 6-1).
     * @param dekKey            Static Key-DEK, same length as {@code encKey}, or {@code null}
     *                          when DEK operations are not needed ([Amd D] Table 6-1).
     * @return a new {@code SecureChannelProfile} ready for use with
     *         {@link SecureChannelSession#create(ApduChannel, SecureChannelProfile)}
     */
    public static SecureChannelProfile scp03(
            byte[] securityDomainAid,
            int keyVersionNumber,
            int keyIdentifier,
            int securityLevel,
            byte[] encKey,
            byte[] macKey,
            byte[] dekKey) {
        return new SecureChannelProfile(
                Scp.SCP03,
                securityDomainAid,
                keyVersionNumber,
                keyIdentifier,
                securityLevel,
                encKey,
                macKey,
                dekKey,
                null,
                null);
    }

    /**
     * Creates a configuration profile for SCP02 (DES-based Secure Channel Protocol).
     *
     * <p><b>SCP02 is deprecated.</b> Its SCP identifier {@code '02'} is listed as
     * "Deprecated, reserved" in [GPCS] §10.7. Prefer {@link #scp03} for new integrations.
     *
     * <p>SCP02 supports both explicit and implicit Secure Channel initiation
     * ([GPCS] §10.2.1 and §10.2.2):
     * <ul>
     *   <li><b>Explicit</b>: {@code scp02SequenceCounter} should be {@code null}; the
     *       card will supply the sequence counter in the INITIALIZE UPDATE response.</li>
     *   <li><b>Implicit</b>: supply {@code scp02SequenceCounter} (2 bytes) so that the
     *       session keys can be derived without a round-trip INITIALIZE UPDATE.</li>
     * </ul>
     *
     * @param securityDomainAid    AID of the Security Domain to select. Must not be null or empty.
     * @param keyVersionNumber     Key Version Number (0x00–0xFF); {@code 0x00} = first available.
     * @param keyIdentifier        Key Identifier within the key set (0x00–0xFF).
     * @param securityLevel        Session security level bitmap.
     * @param encKey               Static ENC key, 16/24/32 bytes.
     * @param macKey               Static MAC key, same length as {@code encKey}.
     * @param dekKey               Static DEK key, same length as {@code encKey}, or {@code null}.
     * @param hostChallenge        8-byte host challenge, or {@code null} for auto-generated.
     * @param scp02SequenceCounter 2-byte sequence counter for implicit initiation
     *                             ([GPCS] §10.2.2), or {@code null} for explicit initiation.
     * @return a new {@code SecureChannelProfile} ready for use with
     *         {@link SecureChannelSession#create(ApduChannel, SecureChannelProfile)}
     */
    public static SecureChannelProfile scp02(
            byte[] securityDomainAid,
            int keyVersionNumber,
            int keyIdentifier,
            int securityLevel,
            byte[] encKey,
            byte[] macKey,
            byte[] dekKey,
            byte[] hostChallenge,
            byte[] scp02SequenceCounter) {
        return new SecureChannelProfile(
                Scp.SCP02,
                securityDomainAid,
                keyVersionNumber,
                keyIdentifier,
                securityLevel,
                encKey,
                macKey,
                dekKey,
                hostChallenge,
                scp02SequenceCounter);
    }

    @Override
    public byte[] securityDomainAid() {
        return Arrays.copyOf(securityDomainAid, securityDomainAid.length);
    }

    @Override
    public byte[] encKey() {
        return Arrays.copyOf(encKey, encKey.length);
    }

    @Override
    public byte[] macKey() {
        return Arrays.copyOf(macKey, macKey.length);
    }

    @Override
    public byte[] dekKey() {
        return dekKey == null ? null : Arrays.copyOf(dekKey, dekKey.length);
    }

    @Override
    public byte[] hostChallenge() {
        return Arrays.copyOf(hostChallenge, hostChallenge.length);
    }

    @Override
    public byte[] scp02SequenceCounter() {
        return scp02SequenceCounter == null ? null : Arrays.copyOf(scp02SequenceCounter, scp02SequenceCounter.length);
    }

    private static byte[] copyRequired(byte[] value) {
        Objects.requireNonNull(value);
        if (value.length == 0) {
            throw new IllegalArgumentException("Value must not be empty");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static byte[] copyKey(byte[] value) {
        Objects.requireNonNull(value);
        if (value.length != 16 && value.length != 24 && value.length != 32) {
            throw new IllegalArgumentException("Key must be 16, 24, or 32 bytes");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static byte[] normalizeHostChallenge(byte[] value) {
        if (value == null) {
            byte[] generated = new byte[8];
            new SecureRandom().nextBytes(generated);
            return generated;
        }
        if (value.length != 8) {
            throw new IllegalArgumentException("hostChallenge must be exactly 8 bytes");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static byte[] normalizeScp02SequenceCounter(byte[] value) {
        if (value == null) {
            return null;
        }
        if (value.length != 2) {
            throw new IllegalArgumentException("scp02SequenceCounter must be exactly 2 bytes when provided");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static void requireCompatibleKeyLengths(byte[] encKey, byte[] macKey, byte[] dekKey) {
        if (encKey.length != macKey.length) {
            throw new IllegalArgumentException("encKey and macKey must use the same length");
        }
        if (dekKey != null && dekKey.length != encKey.length) {
            throw new IllegalArgumentException("dekKey must match encKey/macKey length when present");
        }
    }

}
