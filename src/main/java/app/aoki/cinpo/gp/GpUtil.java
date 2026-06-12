package app.aoki.cinpo.gp;

import java.util.Arrays;

/**
 * GlobalPlatform (GP) domain utility class providing validation, normalization,
 * and transformation helpers shared across GP protocol and installation classes.
 *
 * <p>The methods in this class encode rules drawn directly from:
 * <ul>
 *   <li>GlobalPlatform Card Specification v2.3.1.49 (GPCS) — AID lengths,
 *       privileges coding (§11.1.2), CLA byte coding (§11.1.4), and
 *       INSTALL command parameters (§11.5.2.3.7 / Table 11-49).</li>
 *   <li>GlobalPlatform SCP03 Amendment D v1.1.2 (Amd D) — C-MAC CLA
 *       modification rules (§6.2.4) and security-level bit assignments
 *       (§7.1.2.3, Table 7-6).</li>
 * </ul>
 *
 * <p>None of the methods in this class perform cryptographic operations or
 * communicate with a card; they are pure data-transformation helpers.
 */
public final class GpUtil {

    private GpUtil() {
    }

    // ─── AID validation ─────────────────────────────────────────────────

    /**
     * Validates that {@code aid} is a legal AID and returns a defensive copy.
     *
     * <p>Per ISO 7816-5 §5.2, an Application Identifier (AID) shall be between
     * 5 and 16 bytes in length. The first 5 bytes form the Registered Application
     * Provider Identifier (RID); the optional trailing bytes (0–11) form the
     * Proprietary Application Identifier Extension (PIX).
     *
     * @param aid the AID bytes to validate; must be 5–16 bytes
     * @return a fresh defensive copy of {@code aid}
     * @throws IllegalArgumentException if {@code aid} is null or outside the 5–16 byte range
     */
    static byte[] requireAid(byte[] aid) {
        if (aid == null || aid.length < 5 || aid.length > 16) {
            throw new IllegalArgumentException("AID must be between 5 and 16 bytes");
        }
        return Arrays.copyOf(aid, aid.length);
    }

    /**
     * Normalizes an optional AID value.
     *
     * <p>Returns an empty {@code byte[]} if {@code aid} is {@code null} or has zero length
     * (indicating "no AID specified"). Otherwise delegates to {@link #requireAid} to
     * enforce the ISO 7816-5 5–16 byte constraint and return a defensive copy.
     *
     * @param aid the AID bytes, or {@code null} / empty to indicate absence
     * @return an empty array if absent; otherwise a validated defensive copy of {@code aid}
     * @throws IllegalArgumentException if {@code aid} is present but outside the 5–16 byte range
     */
    static byte[] normalizeOptionalAid(byte[] aid) {
        if (aid == null || aid.length == 0) {
            return new byte[0];
        }
        return requireAid(aid);
    }

    // ─── Privilege normalization ─────────────────────────────────────────

    /**
     * Normalizes a privilege byte array to the format expected by GPCS §11.1.2.
     *
     * <p>Per GPCS §11.1.2 (Tables 11-7, 11-8, 11-9), privileges are coded on
     * <em>three bytes</em>. Byte 1 encodes privileges 0–7 (e.g. Security Domain,
     * DAP Verification, Delegated Management), byte 2 encodes privileges 8–15
     * (e.g. Trusted Path, Token Management, Global Delete), and byte 3 encodes
     * privileges 16–20 (e.g. Receipt Generation, Ciphered Load File Data Block).
     *
     * <p>This method also accepts a single-byte encoding for backward compatibility
     * with legacy callers that supply only the first privilege byte.
     *
     * <ul>
     *   <li>{@code null} — treated as no privileges; returns {@code {0x00, 0x00, 0x00}}.</li>
     *   <li>1 byte — backward-compatible shorthand for byte-1 privileges only.</li>
     *   <li>3 bytes — full three-byte encoding per GPCS §11.1.2.</li>
     * </ul>
     *
     * @param privileges the raw privilege bytes (null, 1 byte, or 3 bytes)
     * @return a defensive copy of {@code privileges}, or {@code {0x00, 0x00, 0x00}} if null
     * @throws IllegalArgumentException if {@code privileges} is present but not 1 or 3 bytes
     */
    static byte[] normalizePrivileges(byte[] privileges) {
        if (privileges == null) {
            return new byte[]{0x00, 0x00, 0x00};
        }
        if (privileges.length != 1 && privileges.length != 3) {
            throw new IllegalArgumentException("privileges must be 1 or 3 bytes");
        }
        return Arrays.copyOf(privileges, privileges.length);
    }

    // ─── Install parameters ──────────────────────────────────────────────

    private static final byte[] DEFAULT_INSTALL_PARAMETERS = {(byte) 0xC9, 0x00};

    /**
     * Normalizes the Install Parameters field for an INSTALL [for install] command.
     *
     * <p>Per GPCS §11.5.2.3.7 (Table 11-49), the Install Parameters field is a
     * TLV-structured value. Tag {@code C9} identifies Application Specific Parameters
     * and is listed as <em>Mandatory</em> in Table 11-49. When the caller supplies
     * {@code null}, this method returns the minimal valid encoding:
     * <pre>
     *   C9 00   →  tag C9, length 0 (no application-specific parameter data)
     * </pre>
     *
     * @param installParameters the raw install-parameters TLV bytes, or {@code null} for default
     * @return a defensive copy of {@code installParameters}, or {@code {0xC9, 0x00}} if null
     */
    static byte[] normalizeInstallParameters(byte[] installParameters) {
        if (installParameters == null) {
            return Arrays.copyOf(DEFAULT_INSTALL_PARAMETERS, DEFAULT_INSTALL_PARAMETERS.length);
        }
        return Arrays.copyOf(installParameters, installParameters.length);
    }

    // ─── Secure messaging CLA ────────────────────────────────────────────

    /**
     * Computes the secure-messaging CLA byte for the APDU to be transmitted to the card,
     * applying the GlobalPlatform secure-messaging indication for the current logical channel.
     *
     * <p>The encoding follows GPCS §11.1.4 and is the same for SCP02 and SCP03.
     * Two cases are distinguished by bit b7 of the incoming {@code logicalCla}:
     *
     * <h3>First interindustry (channels 0–3, b7 = 0) — GPCS Table 11-11</h3>
     * <pre>
     *   b8=1  GlobalPlatform command
     *   b4=0, b3=1  GP proprietary secure messaging (b4b3 = 01)
     *   b2b1  logical channel number (preserved from cla &amp; 0x03)
     * </pre>
     * Result: {@code 0x80 | 0x04 | (cla &amp; 0x03)}
     *
     * <h3>Further interindustry (channels 4–19, b7 = 1) — GPCS Table 11-12</h3>
     * <pre>
     *   b8=1  GlobalPlatform command
     *   b7=1  further interindustry class (channels 4–19)
     *   b6=1  secure messaging (GP proprietary or ISO 7816 format)
     *   b4–b1 logical channel number (preserved from cla &amp; 0x0F)
     * </pre>
     * Result: {@code 0x80 | 0x40 | 0x20 | (cla &amp; 0x0F)}
     *
     * <p><strong>Important caveat for C-MAC calculation (Amd D §6.2.4):</strong><br>
     * Per SCP03 Amendment D §6.2.4, <em>"The logical channel number shall be set to
     * zero, bit 4 shall be set to 0 and bit 3 shall be set to 1"</em> when constructing
     * the APDU header used as input to the C-MAC computation. This method returns the
     * CLA byte for the <em>transmitted</em> APDU (channel bits preserved). Callers that
     * compute C-MAC must apply the channel-zeroing rule separately to the MAC-input
     * header before invoking the MAC function; then restore the channel bits (by calling
     * this method) for the final outgoing APDU.
     *
     * @param logicalCla the original CLA byte of the unsecured command (0x00–0xFF)
     * @return the modified CLA byte with GP secure-messaging bits set and channel bits preserved
     */
    public static int secureMessagingCla(int logicalCla) {
        int cla = logicalCla & 0xFF;
        if ((cla & 0x40) != 0) {
            return 0x20 | (cla & 0x0F) | 0x40 | 0x80;
        }
        return 0x04 | (cla & 0x03) | 0x80;
    }

    // ─── Security level helpers ──────────────────────────────────────────

    /**
     * Returns {@code true} if the security level requires C-MAC (command integrity).
     *
     * <p>Per Amd D §7.1.2.3 Table 7-6 (EXTERNAL AUTHENTICATE P1), bit b1 ({@code 0x01})
     * is set whenever C-MAC is active. C-MAC is the minimum required protection and is a
     * prerequisite for C-DECRYPTION.
     *
     * @param securityLevel the P1 value from EXTERNAL AUTHENTICATE
     * @return {@code true} if bit {@code 0x01} is set
     */
    static boolean usesCMac(int securityLevel) {
        return (securityLevel & 0x01) != 0;
    }

    /**
     * Returns {@code true} if the security level requires C-DECRYPTION (command confidentiality).
     *
     * <p>Per Amd D §7.1.2.3 Table 7-6, C-DECRYPTION is always combined with C-MAC:
     * the two-bit field {@code b2b1} equals {@code 11} (mask {@code 0x03 == 0x03}).
     * A security level of {@code 0x03} means "C-DECRYPTION and C-MAC"; {@code 0x13}
     * adds R-MAC; {@code 0x33} adds both R-MAC and R-ENCRYPTION.
     *
     * @param securityLevel the P1 value from EXTERNAL AUTHENTICATE
     * @return {@code true} if bits {@code b2b1} are both set ({@code securityLevel &amp; 0x03} == {@code 0x03})
     */
    public static boolean usesCDecryption(int securityLevel) {
        return (securityLevel & 0x03) == 0x03;
    }

    /**
     * Returns {@code true} if the security level requires R-MAC (response integrity).
     *
     * <p>Per Amd D §7.1.2.3 Table 7-6, bit b5 ({@code 0x10}) is set whenever R-MAC is
     * active. R-MAC may be present with or without R-ENCRYPTION (e.g. security level
     * {@code 0x11} = C-MAC + R-MAC).
     *
     * @param securityLevel the P1 value from EXTERNAL AUTHENTICATE
     * @return {@code true} if bit {@code 0x10} is set
     */
    public static boolean usesRMac(int securityLevel) {
        return (securityLevel & 0x10) == 0x10;
    }

    /**
     * Returns {@code true} if the security level requires R-ENCRYPTION (response confidentiality).
     *
     * <p>Per Amd D §7.1.2.3 Table 7-6, R-ENCRYPTION is always combined with R-MAC:
     * the two-bit field {@code b6b5} equals {@code 11} (mask {@code 0x30 == 0x30}).
     * A security level of {@code 0x33} is the maximum level: C-DECRYPTION, R-ENCRYPTION,
     * C-MAC, and R-MAC all active.
     *
     * @param securityLevel the P1 value from EXTERNAL AUTHENTICATE
     * @return {@code true} if bits {@code b6b5} are both set ({@code securityLevel &amp; 0x30} == {@code 0x30})
     */
    public static boolean usesREncryption(int securityLevel) {
        return (securityLevel & 0x30) == 0x30;
    }

}
