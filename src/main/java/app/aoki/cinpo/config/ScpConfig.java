package app.aoki.cinpo.config;

import app.aoki.cinpo.gp.scp.SecureChannelProfile;

import java.util.Arrays;
import java.util.Objects;

/**
 * GlobalPlatform Secure Channel Protocol (SCP) configuration.
 *
 * <p>Contains all parameters necessary to establish an authenticated secure channel
 * with a GlobalPlatform Security Domain:
 * <ul>
 *   <li>Protocol variant (scp02, scp03)</li>
 *   <li>Security Domain AID</li>
 *   <li>Key set addressing (key version number, key identifier)</li>
 *   <li>Security level bitmap</li>
 *   <li>Static key material (ENC, MAC, DEK)</li>
 * </ul>
 *
 * <p>This configuration object can be converted to a {@link SecureChannelProfile}
 * via {@link #toSecureChannelProfile()} for use with the SCP framework.
 *
 * @param protocol          SCP protocol variant ("scp02" or "scp03")
 * @param isdAid            Security Domain AID (ISD or Supplementary Security Domain)
 * @param keyVersionNumber  Key Version Number (0x00-0xFF)
 * @param keyIdentifier     Key Identifier within key set (0x00-0xFF)
 * @param securityLevel     Security level bitmap per GP specification
 * @param encKey            Static Encryption Key (16, 24, or 32 bytes)
 * @param macKey            Static MAC Key (must match encKey length)
 * @param dekKey            Data Encryption Key (must match encKey length)
 * @see SecureChannelProfile
 */
public record ScpConfig(
        String protocol,
        byte[] isdAid,
        int keyVersionNumber,
        int keyIdentifier,
        int securityLevel,
        byte[] encKey,
        byte[] macKey,
        byte[] dekKey
) {
    public ScpConfig {
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(isdAid);
        Objects.requireNonNull(encKey);
        Objects.requireNonNull(macKey);
        Objects.requireNonNull(dekKey);

        if (protocol.isBlank()) {
            throw new IllegalArgumentException("protocol must not be blank");
        }
        if (isdAid.length == 0) {
            throw new IllegalArgumentException("isdAid must not be empty");
        }
        if (keyVersionNumber < 0x00 || keyVersionNumber > 0xFF) {
            throw new IllegalArgumentException("keyVersionNumber must be between 0x00 and 0xFF");
        }
        if (keyIdentifier < 0x00 || keyIdentifier > 0xFF) {
            throw new IllegalArgumentException("keyIdentifier must be between 0x00 and 0xFF");
        }
        if (securityLevel < 0x00 || securityLevel > 0xFF) {
            throw new IllegalArgumentException("securityLevel must be between 0x00 and 0xFF");
        }

        // Defensive copies
        isdAid = Arrays.copyOf(isdAid, isdAid.length);
        encKey = Arrays.copyOf(encKey, encKey.length);
        macKey = Arrays.copyOf(macKey, macKey.length);
        dekKey = Arrays.copyOf(dekKey, dekKey.length);
    }

    /**
     * Returns a defensive copy of the ISD AID.
     */
    @Override
    public byte[] isdAid() {
        return Arrays.copyOf(isdAid, isdAid.length);
    }

    /**
     * Returns a defensive copy of the encryption key.
     */
    @Override
    public byte[] encKey() {
        return Arrays.copyOf(encKey, encKey.length);
    }

    /**
     * Returns a defensive copy of the MAC key.
     */
    @Override
    public byte[] macKey() {
        return Arrays.copyOf(macKey, macKey.length);
    }

    /**
     * Returns a defensive copy of the DEK key.
     */
    @Override
    public byte[] dekKey() {
        return Arrays.copyOf(dekKey, dekKey.length);
    }

    /**
     * Converts this configuration to a {@link SecureChannelProfile} suitable for
     * establishing a secure channel session.
     *
     * @return a new {@link SecureChannelProfile} instance
     * @throws IllegalArgumentException if protocol is not recognized
     */
    public SecureChannelProfile toSecureChannelProfile() {
        return switch (protocol.toLowerCase()) {
            case "scp02" -> SecureChannelProfile.scp02(
                    isdAid(),
                    keyVersionNumber,
                    keyIdentifier,
                    securityLevel,
                    encKey(),
                    macKey(),
                    dekKey(),
                    null,  // hostChallenge - auto-generated
                    null   // scp02SequenceCounter - explicit initiation
            );
            case "scp03" -> SecureChannelProfile.scp03(
                    isdAid(),
                    keyVersionNumber,
                    keyIdentifier,
                    securityLevel,
                    encKey(),
                    macKey(),
                    dekKey()
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported SCP protocol: " + protocol + ". Supported: scp02, scp03"
            );
        };
    }
}
