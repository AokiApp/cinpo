package app.aoki.cinpo.gp.scp;

import static app.aoki.cinpo.apdu.ApduUtil.assertSwOk;
import static app.aoki.cinpo.util.Util.concat;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.BerTlv;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.gp.GpCommands;
import app.aoki.cinpo.gp.GpCrypto;
import app.aoki.cinpo.gp.GpUtil;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Implements the legacy <em>Secure Channel Protocol '02'</em> (SCP02) for authentication and
 * secure command transmission.
 *
 * <p><strong>Deprecation / voided status:</strong> SCP02 was originally specified in Appendix E of
 * the GlobalPlatform Card Specification. As stated in the GPCS v2.3.1.49 revision history
 * (§1.6.8, page 36): <em>"Annex E describing Secure Channel Protocol '02' is now void. All
 * references to SCP02 are removed."</em> The SCP03 Amendment D v1.1.2 revision history (March
 * 2019) likewise noted: <em>"SCP02 has been deprecated and will be removed from [GPCS]."</em>
 * New implementations should prefer SCP03 (AES-based).
 *
 * <p><strong>Cryptographic basis:</strong> SCP02 uses Triple-DES (3DES / 2TDEA) throughout:
 * <ul>
 *   <li>Session-key derivation: 3DES-CBC encryption of a 16-byte derivation data block with a
 *       zero IV (§ "Data Derivation Scheme", former GPCS Appendix E / [Amd D] §4.1.5).</li>
 *   <li>C-MAC: Retail MAC (ISO 9797-1 Algorithm 3) — single-DES for all but the last 8-byte
 *       block, then 3DES for the final block — applied over ISO 9797-1 Method 2 padded input.
 *       The MAC session key ({@code S-MAC}) is used.</li>
 *   <li>Cryptograms (card / host): full 3DES-CBC-MAC (ISO 9797-1 Algorithm 3 variant) over
 *       challenge data using the ENC session key ({@code S-ENC}).</li>
 * </ul>
 *
 * <p><strong>Limitations of this implementation:</strong>
 * <ul>
 *   <li>Command data encryption (C-DECRYPTION, security level bit {@code 0x02}) is
 *       <em>not implemented</em> — see {@code SECURITY_LEVEL_C_DECRYPTION}.</li>
 *   <li>Response MAC (R-MAC, security level bit {@code 0x10}) is <em>not implemented</em> even
 *       when the card advertises R-MAC support via the implementation option bitmap.</li>
 * </ul>
 */
final class Scp02Protocol implements InternalSecureChannelProtocol {

    private static final int SECURITY_LEVEL_C_MAC = 0x01;
    private static final int SECURITY_LEVEL_C_DECRYPTION = 0x02;
    private static final int SECURITY_LEVEL_R_MAC = 0x10;
    private static final int SUPPORTED_SECURITY_LEVEL_MASK = SECURITY_LEVEL_C_MAC | SECURITY_LEVEL_C_DECRYPTION | SECURITY_LEVEL_R_MAC;
    private static final byte[] ZERO_IV = new byte[8];
    private static final byte[] SCP02_IMPLEMENTATION_OPTION_OID_PREFIX = {
        0x2A,
        (byte) 0x86,
        0x48,
        (byte) 0x86,
        (byte) 0xFC,
        0x6B,
        0x04,
        0x02
    };

    private final SecureChannelProfile profile;

    private Scp02ImplementationOptions implementationOptions;
    private byte[] sessionEncKey;
    private byte[] sessionMacKey;
    private byte[] sessionRmacKey;
    private byte[] sequenceCounter;
    private byte[] cardChallenge;
    private byte[] currentIcv;
    private boolean authenticated;
    private boolean hasCmac;

    Scp02Protocol(SecureChannelProfile profile) {
        this.profile = profile;
        validateStaticConfiguration();
    }

    /**
     * Performs SCP02 authentication and initializes secure-messaging state.
     *
     * <p><strong>Precondition:</strong> the card's Security Domain identified by
     * {@link SecureChannelProfile#securityDomainAid()} must already be selected before calling
     * this method. Callers are responsible for issuing the plain SELECT command.
     *
     * <p>The flow starts by discovering implementation options, then performs explicit
     * (INITIALIZE UPDATE + EXTERNAL AUTHENTICATE) or implicit initiation depending on the card
     * configuration.
     *
     * @param channel the raw APDU transport channel
     * @throws IllegalStateException if SCP02 setup or cryptographic checks fail
     */
    @Override
    public void authenticate(ApduChannel channel) {
        try {
            implementationOptions = discoverImplementationOptions(channel);
            validateImplementationOptions();

            if (implementationOptions.explicitInitiation()) {
                authenticateExplicit(channel);
            } else {
                authenticateImplicit();
            }
            authenticated = true;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SCP02 cryptographic setup failed", e);
        }
    }

    @Override
    public ResponseApdu transmit(ApduChannel channel, CommandApdu capdu) {
        if (!authenticated) {
            throw new IllegalStateException("SCP02 secure channel is not authenticated");
        }
        try {
            CommandApdu wrapped = wrapCommand(capdu);
            return channel.transmit(wrapped);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SCP02 secure messaging failed", e);
        }
    }

    @Override
    public void close() {
        authenticated = false;
        hasCmac = false;
        implementationOptions = null;
        sequenceCounter = null;
        cardChallenge = null;
        sessionEncKey = null;
        sessionMacKey = null;
        sessionRmacKey = null;
        currentIcv = null;
    }

    private void validateStaticConfiguration() {
        if (profile.encKey().length != 16 || profile.macKey().length != 16) {
            throw new IllegalStateException("SCP02 currently requires 16-byte ENC and MAC keys");
        }
        if (profile.dekKey() != null && profile.dekKey().length != 16) {
            throw new IllegalStateException("SCP02 currently requires a 16-byte DEK when DEK is provided");
        }
        if ((profile.securityLevel() & ~SUPPORTED_SECURITY_LEVEL_MASK) != 0) {
            throw new IllegalStateException(
                    "Unsupported SCP02 security level bits: " + Integer.toHexString(profile.securityLevel() & ~SUPPORTED_SECURITY_LEVEL_MASK));
        }
        if ((profile.securityLevel() & SECURITY_LEVEL_C_DECRYPTION) != 0) {
            // C-DECRYPTION (bit 0x02) is defined in former GPCS Appendix E but is not implemented
            // here. SCP02 command encryption requires 3DES-CBC encryption of padded command data
            // using S-ENC, applied after C-MAC calculation. This path always throws so that
            // callers cannot accidentally send plaintext data believing it is encrypted.
            throw new IllegalStateException(
                    "Unsupported SCP02 security level: command data encryption is not implemented");
        }
    }

    /**
     * Issues GET DATA [P1=00, P2=66] to the currently selected Security Domain and extracts the
     * SCP02 implementation option byte from the Card Recognition Data.
     *
     * <p>Card Recognition Data (tag {@code 66}) is retrieved using CLA {@code 80} INS {@code CA}
     * P1-P2 {@code 00 66} as defined in GPCS §11.3 (Table 11-27). A GlobalPlatform proprietary
     * class byte (bit b8 = 1) is required so that the response contains the full BER-TLV object.
     *
     * <p>The response is parsed according to GPCS Appendix H, Tables H-1 / H-2:
     * <ol>
     *   <li>The outer object must carry tag {@code 66} (Card Data).</li>
     *   <li>Inside tag {@code 66}, one or more tag {@code 73} (Card Recognition Data) templates
     *       are expected.</li>
     *   <li>Inside tag {@code 73}, tag {@code 64} (Application tag 4) objects embed OIDs that
     *       identify the supported Secure Channel Protocol and its implementation options.
     *       The OID has the ASN.1 form {@code {globalPlatform 4 scp i}} where {@code scp} is the
     *       SCP identifier and {@code i} is the implementation option byte.</li>
     *   <li>For SCP02 the OID prefix is {@code 1.2.840.114283.4.2} (encoded as
     *       {@code 2A 86 48 86 FC 6B 04 02}); the final sub-identifier of the OID encodes the
     *       implementation option bitmap.</li>
     * </ol>
     *
     * <p>If GET DATA fails or no SCP02 OID is found, falls back to
     * {@link #inferImplementationOptionsFallback(String)}.
     */
    private Scp02ImplementationOptions discoverImplementationOptions(ApduChannel channel) {
        ResponseApdu response = channel.transmit(new CommandApdu(0x80, Iso7816Commands.INS_GET_DATA, 0x00, 0x66, null, 256));
        if (response.sw() == 0x9000) {
            Integer optionValue = parseScp02ImplementationOption(response.data());
            if (optionValue != null) {
                return Scp02ImplementationOptions.parse(optionValue);
            }
            return inferImplementationOptionsFallback(
                    "Card Recognition Data does not advertise an SCP02 implementation option");
        }
        return inferImplementationOptionsFallback(
                "GET DATA [0066] failed with SW=" + Integer.toHexString(response.sw()));
    }

    private Scp02ImplementationOptions inferImplementationOptionsFallback(String reason) {
        boolean keysDiffer = !Arrays.equals(profile.encKey(), profile.macKey())
                || (profile.dekKey() != null && !Arrays.equals(profile.encKey(), profile.dekKey()));
        if (keysDiffer && profile.keyIdentifier() != 0x00) {
            throw new IllegalStateException(
                    "Unable to infer SCP02 implementation options from " + reason
                            + "; distinct ENC/MAC/DEK keys require key identifier 0x00 for the current fallback");
        }
        if (profile.scp02SequenceCounter() != null) {
            throw new IllegalStateException(
                    "Unable to infer SCP02 implementation options from " + reason
                            + "; the current fallback only supports explicit-initiation cards");
        }

        int inferredValue = 0x04 | 0x10;
        if (keysDiffer || profile.keyIdentifier() == 0x00) {
            inferredValue |= 0x01;
        }
        return Scp02ImplementationOptions.parse(inferredValue);
    }

    private void validateImplementationOptions() {
        if (implementationOptions == null) {
            throw new IllegalStateException("SCP02 implementation options are not initialized");
        }
        if (implementationOptions.explicitInitiation()) {
            if (profile.scp02SequenceCounter() != null && profile.scp02SequenceCounter().length != 2) {
                throw unsupportedImplementationOption("explicit initiation ignores scp02SequenceCounter, but an invalid value was provided");
            }
        } else if (profile.scp02SequenceCounter() == null) {
            throw unsupportedImplementationOption(
                    "implicit initiation requires scp02SequenceCounter in "
                            + SecureChannelProfile.class.getSimpleName());
        }

        if (implementationOptions.usesThreeSecureChannelKeys()) {
            if (profile.keyIdentifier() != 0x00) {
                throw unsupportedImplementationOption(
                        "only key identifier 0x00 is supported when the card advertises 3 Secure Channel Keys");
            }
        } else {
            validateBaseKeyConfiguration();
        }

        if ((profile.securityLevel() & SECURITY_LEVEL_R_MAC) != 0) {
            if (!implementationOptions.rmacSupported()) {
                throw unsupportedImplementationOption(
                        "security level requests R-MAC but card implementation option does not advertise R-MAC");
            }
            throw unsupportedImplementationOption("R-MAC is advertised/requested but host-side R-MAC is not implemented");
        }
    }

    private void validateBaseKeyConfiguration() {
        byte[] enc = profile.encKey();
        byte[] mac = profile.macKey();
        if (!Arrays.equals(enc, mac)) {
            throw unsupportedImplementationOption(
                    "base-key mode requires encKey and macKey to contain the same static base key bytes");
        }
        byte[] dek = profile.dekKey();
        if (dek != null && !Arrays.equals(enc, dek)) {
            throw unsupportedImplementationOption(
                    "base-key mode requires dekKey to match encKey/macKey when provided");
        }
    }

    private IllegalStateException unsupportedImplementationOption(String reason) {
        return new IllegalStateException(
                "Unsupported SCP02 implementation option i=" + implementationOptions.hexValue() + ": " + reason);
    }

    private void authenticateExplicit(ApduChannel channel) throws GeneralSecurityException {
        ResponseApdu initializeResponse = assertSwOk(channel.transmit(GpCommands.initializeUpdate(
                profile.keyVersionNumber(),
                keyIdentifierForInitializeUpdate(),
                profile.hostChallenge())));

        InitializeUpdateResponse parsed = InitializeUpdateResponse.parse(initializeResponse.data());
        if (parsed.scpIdentifier() != 0x02) {
            throw new IllegalStateException(
                    "Card did not return SCP02 in INITIALIZE UPDATE response (scpId="
                            + Integer.toHexString(parsed.scpIdentifier()) + ")");
        }
        sequenceCounter = parsed.sequenceCounter();
        cardChallenge = parsed.cardChallenge();
        deriveSessionKeys();
        validateAdvertisedCardChallengeAlgorithm();

        byte[] expectedCardCryptogram = calculateCardCryptogram();
        if (!MessageDigest.isEqual(expectedCardCryptogram, parsed.cardCryptogram())) {
            throw new IllegalStateException(
                    "SCP02 card cryptogram verification failed; EXTERNAL AUTHENTICATE was not sent");
        }

        resetCommandMacState();
        byte[] hostCryptogram = calculateHostCryptogram();
        CommandApdu externalAuthenticate = wrapExternalAuthenticate(hostCryptogram);
        assertSwOk(channel.transmit(externalAuthenticate));
    }

    private void authenticateImplicit() throws GeneralSecurityException {
        sequenceCounter = profile.scp02SequenceCounter();
        cardChallenge = null;
        deriveSessionKeys();
        resetCommandMacState();
    }

    private void validateAdvertisedCardChallengeAlgorithm() throws GeneralSecurityException {
        if (!implementationOptions.wellKnownCardChallengeAlgorithm()) {
            return;
        }
        byte[] expectedCardChallenge = deriveWellKnownCardChallenge();
        if (!MessageDigest.isEqual(expectedCardChallenge, cardChallenge)) {
            throw new IllegalStateException(
                    "SCP02 card challenge does not match the well-known pseudo-random algorithm advertised by i="
                            + implementationOptions.hexValue());
        }
    }

    private int keyIdentifierForInitializeUpdate() {
        if (implementationOptions.usesThreeSecureChannelKeys()) {
            return 0x00;
        }
        return profile.keyIdentifier();
    }

    private void resetCommandMacState() {
        currentIcv = Arrays.copyOf(ZERO_IV, ZERO_IV.length);
        hasCmac = false;
    }

    private void deriveSessionKeys() throws GeneralSecurityException {
        sessionEncKey  = deriveSessionKey(staticEncKeyForSession(),  0x01, 0x82);
        sessionMacKey  = deriveSessionKey(staticMacKeyForSession(),  0x01, 0x01);
        sessionRmacKey = deriveSessionKey(staticMacKeyForSession(),  0x01, 0x02);
    }

    private byte[] staticEncKeyForSession() {
        return implementationOptions.usesThreeSecureChannelKeys() ? profile.encKey() : baseKey();
    }

    private byte[] staticMacKeyForSession() {
        return implementationOptions.usesThreeSecureChannelKeys() ? profile.macKey() : baseKey();
    }

    private byte[] baseKey() {
        return profile.encKey();
    }

    /**
     * Derives a single SCP02 session key from a static base key.
     *
     * <p>Derivation procedure (former GPCS Appendix E / [Amd D] §4.1.5 – Data Derivation Scheme):
     * <pre>
     *   derivationData[0]    = b0   (constant byte, high)
     *   derivationData[1]    = b1   (constant byte, low — identifies key purpose):
     *                                 0x82 → S-ENC  (constant word 0x0182)
     *                                 0x01 → S-MAC  (constant word 0x0101)
     *                                 0x02 → S-RMAC (constant word 0x0102)
     *   derivationData[2..3] = sequenceCounter  (2 bytes, big-endian)
     *   derivationData[4..15]= 0x00 (zero-padded)
     *
     *   result = 3DES-CBC-ENC(expand3DesKey(staticKey), IV=0x00…, derivationData)[0..15]
     * </pre>
     *
     * <p>The 3DES encryption produces 16 bytes (two 8-byte DES blocks); the result is truncated
     * to 16 bytes to produce a 2TDEA session key.
     *
     * @param staticKey the static 16-byte base/ENC/MAC key from {@link SecureChannelProfile}
     * @param b0        high byte of the derivation constant (always {@code 0x01} for SCP02)
     * @param b1        low byte of the derivation constant ({@code 0x82}, {@code 0x01}, or
     *                  {@code 0x02})
     * @return the 16-byte derived session key
     */
    private byte[] deriveSessionKey(byte[] staticKey, int b0, int b1) throws GeneralSecurityException {
        byte[] derivationData = new byte[16];
        derivationData[0] = (byte) b0;
        derivationData[1] = (byte) b1;
        System.arraycopy(sequenceCounter, 0, derivationData, 2, 2);
        return Arrays.copyOf(GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(staticKey), ZERO_IV, derivationData), 16);
    }

    /**
     * Calculates the expected card cryptogram for mutual authentication.
     *
     * <p>The card cryptogram is a full 3DES-CBC-MAC (ISO 9797-1 Algorithm 3) over ISO 9797-1
     * Method 2 padded input, computed with the {@code S-ENC} session key and a zero IV:
     * <pre>
     *   input  = hostChallenge (8 bytes) || sequenceCounter (2 bytes) || cardChallenge (6 bytes)
     *   mac    = full3DesCbcMac(S-ENC, IV=0, pad2(input))
     *   result = mac[last 8 bytes]   (i.e. the final 8-byte MAC block)
     * </pre>
     *
     * <p>The card returns this value in the INITIALIZE UPDATE response (bytes [20..27]). If the
     * computed value does not match, EXTERNAL AUTHENTICATE is not sent.
     */
    private byte[] calculateCardCryptogram() throws GeneralSecurityException {
        byte[] input = concat(profile.hostChallenge(), sequenceCounter, cardChallenge);
        byte[] mac = GpCrypto.full3DesCbcMac(sessionEncKey, GpCrypto.padIso9797Method2(input, 8));
        return Arrays.copyOfRange(mac, mac.length - 8, mac.length);
    }

    /**
     * Calculates the host cryptogram sent in the EXTERNAL AUTHENTICATE command.
     *
     * <p>The host cryptogram is a full 3DES-CBC-MAC (ISO 9797-1 Algorithm 3) over ISO 9797-1
     * Method 2 padded input, computed with the {@code S-ENC} session key and a zero IV:
     * <pre>
     *   input  = sequenceCounter (2 bytes) || cardChallenge (6 bytes) || hostChallenge (8 bytes)
     *   mac    = full3DesCbcMac(S-ENC, IV=0, pad2(input))
     *   result = mac[last 8 bytes]
     * </pre>
     *
     * <p>Note that the input field order is the <em>reverse</em> of the card cryptogram input:
     * the sequence counter and card challenge precede the host challenge here.
     */
    private byte[] calculateHostCryptogram() throws GeneralSecurityException {
        byte[] input = concat(sequenceCounter, cardChallenge, profile.hostChallenge());
        byte[] mac = GpCrypto.full3DesCbcMac(sessionEncKey, GpCrypto.padIso9797Method2(input, 8));
        return Arrays.copyOfRange(mac, mac.length - 8, mac.length);
    }

    private byte[] deriveWellKnownCardChallenge() throws GeneralSecurityException {
        byte[] aidMac = calculateAidMac(sessionMacKey);
        return Arrays.copyOf(aidMac, 6);
    }

    private CommandApdu wrapExternalAuthenticate(byte[] hostCryptogram) throws GeneralSecurityException {
        CommandApdu capdu = GpCommands.externalAuthenticate(profile.securityLevel(), hostCryptogram);
        return wrapCommand(capdu);
    }

    private CommandApdu wrapCommand(CommandApdu capdu) throws GeneralSecurityException {
        int secureCla = GpUtil.secureMessagingCla(capdu.cla());
        byte[] commandData = capdu.data();
        byte[] mac = calculateCommandMac(secureCla, capdu.ins(), capdu.p1(), capdu.p2(), commandData);
        byte[] wrappedData = concat(commandData, mac);
        if (capdu.hasLe()) {
            return new CommandApdu(secureCla, capdu.ins(), capdu.p1(), capdu.p2(), wrappedData, 256);
        }
        return new CommandApdu(secureCla, capdu.ins(), capdu.p1(), capdu.p2(), wrappedData);
    }

    /**
     * Calculates the C-MAC for the next command APDU and advances the ICV state.
     *
     * <p>SCP02 C-MAC uses the <em>Retail MAC</em> algorithm (ISO 9797-1 Algorithm 3):
     * single-DES CBC over all but the last 8-byte block, then 3DES (2TDEA) for the final block,
     * using the {@code S-MAC} session key.
     *
     * <p>ICV handling:
     * <ul>
     *   <li>For the first command of a session, the ICV is resolved by
     *       {@link #resolveInitialIcv()}: zero for explicit initiation, or a MAC over the
     *       Security Domain AID when {@code initialIcvIsMacOverAid()} is set.</li>
     *   <li>For subsequent commands, the ICV is the full 8-byte C-MAC of the previous command,
     *       optionally encrypted with single-DES ECB using the first 8 bytes of {@code S-MAC}
     *       when {@code icvEncryptionForCmac()} is set (former GPCS Appendix E bit 4).</li>
     * </ul>
     *
     * <p>MAC input construction (see {@link #buildMacInput}):
     * <ul>
     *   <li>If {@code cmacOnUnmodifiedApdu()} (bit 1): header uses the secure CLA byte and the
     *       original Lc (without room for the 8-byte MAC).</li>
     *   <li>Otherwise: CLA low bits are set to {@code b3b2=01} (GlobalPlatform secure messaging,
     *       GPCS §11.1.4.1 Table 11-11) and Lc is pre-incremented by 8 to include the MAC.</li>
     * </ul>
     *
     * <p>After computation, {@code currentIcv} is updated to the new full 8-byte MAC and
     * {@code hasCmac} is set to {@code true}.
     *
     * @return the 8-byte C-MAC to append to the command data
     */
    private byte[] calculateCommandMac(int secureCla, int ins, int p1, int p2, byte[] data)
            throws GeneralSecurityException {
        byte[] macInput = buildMacInput(secureCla, ins, p1, p2, data);
        byte[] icv = resolveIcvForNextCommand();
        byte[] padded = GpCrypto.padIso9797Method2(macInput, 8);
        byte[] fullMac = GpCrypto.retailMac(sessionMacKey, icv, padded);
        currentIcv = Arrays.copyOf(fullMac, fullMac.length);
        hasCmac = true;
        return Arrays.copyOf(currentIcv, currentIcv.length);
    }

    private byte[] buildMacInput(int secureCla, int ins, int p1, int p2, byte[] data) {
        byte[] body = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        if (implementationOptions.cmacOnUnmodifiedApdu()) {
            return concat(new byte[]{(byte) secureCla, (byte) ins, (byte) p1, (byte) p2, (byte) body.length}, body);
        }
        int modifiedCla = modifiedClaForCmac(secureCla);
        int modifiedLc = body.length + 8;
        return concat(new byte[]{(byte) modifiedCla, (byte) ins, (byte) p1, (byte) p2, (byte) modifiedLc}, body);
    }

    private byte[] resolveIcvForNextCommand() throws GeneralSecurityException {
        if (!hasCmac) {
            return resolveInitialIcv();
        }
        if (!implementationOptions.icvEncryptionForCmac()) {
            return Arrays.copyOf(currentIcv, currentIcv.length);
        }
        return encryptIcv(currentIcv);
    }

    private byte[] resolveInitialIcv() throws GeneralSecurityException {
        if (implementationOptions.explicitInitiation()) {
            return Arrays.copyOf(ZERO_IV, ZERO_IV.length);
        }
        if (implementationOptions.initialIcvIsMacOverAid()) {
            return calculateAidMac(sessionMacKey);
        }
        return Arrays.copyOf(ZERO_IV, ZERO_IV.length);
    }

    private byte[] calculateAidMac(byte[] macKey) throws GeneralSecurityException {
        byte[] paddedAid = GpCrypto.padIso9797Method2(profile.securityDomainAid(), 8);
        return GpCrypto.retailMac(macKey, ZERO_IV, paddedAid);
    }

    private byte[] encryptIcv(byte[] icv) throws GeneralSecurityException {
        byte[] desKey = Arrays.copyOf(sessionMacKey, 8);
        return GpCrypto.desEcbEncrypt(desKey, icv);
    }

    private int modifiedClaForCmac(int secureCla) {
        return (secureCla & 0xFC) | 0x04;
    }

    private static Integer parseScp02ImplementationOption(byte[] cardRecognitionData) {
        BerTlv.Tlv outer = BerTlv.parseSingleTlv(cardRecognitionData, "Card Recognition Data");
        if (outer.tag() != 0x66) {
            throw new IllegalStateException(
                    "Card Recognition Data must start with tag 66, but found " + Integer.toHexString(outer.tag()));
        }

        for (BerTlv.Tlv template : BerTlv.parseTlvs(outer.value())) {
            if (template.tag() != 0x73) {
                continue;
            }
            Integer parsed = parseScp02ImplementationOptionFromTemplate(template.value());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Integer parseScp02ImplementationOptionFromTemplate(byte[] cardRecognitionTemplate) {
        for (BerTlv.Tlv child : BerTlv.parseTlvs(cardRecognitionTemplate)) {
            if (child.tag() != 0x64) {
                continue;
            }
            for (BerTlv.Tlv nested : BerTlv.parseTlvs(child.value())) {
                if (nested.tag() != 0x06) {
                    continue;
                }
                Integer optionValue = decodeScp02ImplementationOptionOid(nested.value());
                if (optionValue != null) {
                    return optionValue;
                }
            }
        }
        return null;
    }

    private static Integer decodeScp02ImplementationOptionOid(byte[] oidValue) {
        if (oidValue.length <= SCP02_IMPLEMENTATION_OPTION_OID_PREFIX.length) {
            return null;
        }
        for (int i = 0; i < SCP02_IMPLEMENTATION_OPTION_OID_PREFIX.length; i++) {
            if (oidValue[i] != SCP02_IMPLEMENTATION_OPTION_OID_PREFIX[i]) {
                return null;
            }
        }

        int subidentifier = 0;
        for (int i = SCP02_IMPLEMENTATION_OPTION_OID_PREFIX.length; i < oidValue.length; i++) {
            int value = oidValue[i] & 0xFF;
            subidentifier = (subidentifier << 7) | (value & 0x7F);
            if ((value & 0x80) == 0) {
                if (i != oidValue.length - 1) {
                    return null;
                }
                return subidentifier;
            }
        }
        throw new IllegalStateException("SCP02 implementation option OID is truncated");
    }

    /**
     * Parsed representation of the INITIALIZE UPDATE response data for SCP02.
     *
     * <p>The INITIALIZE UPDATE response is at least 28 bytes. Field offsets (0-based) within
     * the response data array (former GPCS Appendix E):
     * <pre>
     *   [0..9]   Key Diversification Data  (10 bytes, not used by this implementation)
     *   [10]     Key Version Number        (1 byte)
     *   [11]     SCP Identifier            (1 byte, expected value: 0x02 for SCP02)
     *   [12..13] Sequence Counter          (2 bytes, big-endian)
     *   [14..19] Card Challenge            (6 bytes)
     *   [20..27] Card Cryptogram           (8 bytes)
     * </pre>
     */
    private record InitializeUpdateResponse(
            int keyVersionNumber,
            int scpIdentifier,
            byte[] sequenceCounter,
            byte[] cardChallenge,
            byte[] cardCryptogram) {

        private static InitializeUpdateResponse parse(byte[] responseData) {
            if (responseData == null || responseData.length < 28) {
                throw new IllegalStateException("SCP02 INITIALIZE UPDATE response must be at least 28 bytes");
            }
            return new InitializeUpdateResponse(
                    responseData[10] & 0xFF,
                    responseData[11] & 0xFF,
                    Arrays.copyOfRange(responseData, 12, 14),
                    Arrays.copyOfRange(responseData, 14, 20),
                    Arrays.copyOfRange(responseData, 20, 28));
        }
    }

    /**
     * Decoded SCP02 implementation option bitmap, formerly defined in GPCS Appendix E (now void).
     *
     * <p>The implementation option is advertised by the card as an OID sub-identifier in the
     * Card Recognition Data (GPCS Appendix H, Tables H-1 / H-2, tag {@code 64}, OID arc
     * {@code {globalPlatform 4 2 i}} where {@code i} is this bitmap value). Its encoding is a
     * 7-bit bitmap (values {@code 0x00..0x7F}); each bit selects a specific behaviour:
     *
     * <table border="1" summary="SCP02 implementation option bits">
     *   <tr><th>Bit mask</th><th>Meaning when set</th></tr>
     *   <tr><td>{@code 0x01} (bit 0)</td><td>3 Secure Channel Keys (ENC, MAC, DEK are distinct)</td></tr>
     *   <tr><td>{@code 0x02} (bit 1)</td><td>C-MAC on unmodified APDU (Lc not pre-incremented)</td></tr>
     *   <tr><td>{@code 0x04} (bit 2)</td><td>Explicit Secure Channel Initiation (INITIALIZE UPDATE + EXTERNAL AUTHENTICATE)</td></tr>
     *   <tr><td>{@code 0x08} (bit 3)</td><td>Initial ICV is MAC over the AID of the Security Domain</td></tr>
     *   <tr><td>{@code 0x10} (bit 4)</td><td>ICV encryption for C-MAC (single-DES ECB of previous MAC)</td></tr>
     *   <tr><td>{@code 0x20} (bit 5)</td><td>R-MAC support (card can generate response MACs)</td></tr>
     *   <tr><td>{@code 0x40} (bit 6)</td><td>Well-known pseudo-random card challenge algorithm</td></tr>
     * </table>
     *
     * <p><strong>R-MAC caveat:</strong> even when bit 5 is set and R-MAC is requested via the
     * security level, this implementation always throws — R-MAC is not implemented.
     */
    private record Scp02ImplementationOptions(int value) {

        private Scp02ImplementationOptions {
            if (value < 0x00 || value > 0x7F) {
                throw new IllegalArgumentException(
                        "SCP02 implementation option must fit in the Appendix E bitmap range 0x00..0x7F");
            }
        }

        private static Scp02ImplementationOptions parse(int value) {
            return new Scp02ImplementationOptions(value);
        }

        /**
         * Returns {@code true} when bit 0 ({@code 0x01}) is set, indicating the card uses three
         * distinct Secure Channel Keys: a separate ENC key, MAC key, and DEK. When clear, a single
         * base key is used for all three derivations.
         */
        private boolean usesThreeSecureChannelKeys() {
            return (value & 0x01) != 0;
        }

        /**
         * Returns {@code true} when bit 1 ({@code 0x02}) is set, indicating that C-MAC is
         * computed over the unmodified APDU header (with the original Lc, not pre-incremented
         * by 8 to account for the MAC size).
         */
        private boolean cmacOnUnmodifiedApdu() {
            return (value & 0x02) != 0;
        }

        /**
         * Returns {@code true} when bit 2 ({@code 0x04}) is set, indicating that the Secure
         * Channel uses <em>explicit initiation</em>: the host must send INITIALIZE UPDATE and
         * EXTERNAL AUTHENTICATE to establish the session. When clear, the channel uses
         * <em>implicit initiation</em> (sequence counter and keys are pre-agreed out-of-band).
         */
        private boolean explicitInitiation() {
            return (value & 0x04) != 0;
        }

        /**
         * Returns {@code true} when bit 3 ({@code 0x08}) is set, indicating that the initial
         * ICV for C-MAC computation is a Retail MAC over the AID of the Security Domain (rather
         * than a zero IV). Only relevant for implicit-initiation mode.
         */
        private boolean initialIcvIsMacOverAid() {
            return (value & 0x08) != 0;
        }

        /**
         * Returns {@code true} when bit 4 ({@code 0x10}) is set, indicating that the ICV for
         * each subsequent C-MAC is the single-DES ECB encryption of the previous C-MAC value
         * (using the first 8 bytes of {@code S-MAC}). When clear, the previous C-MAC value is
         * used directly as the next ICV.
         */
        private boolean icvEncryptionForCmac() {
            return (value & 0x10) != 0;
        }

        /**
         * Returns {@code true} when bit 5 ({@code 0x20}) is set, indicating that the card
         * supports Response MAC (R-MAC). This implementation always throws if R-MAC is requested
         * — host-side R-MAC computation is not implemented.
         */
        private boolean rmacSupported() {
            return (value & 0x20) != 0;
        }

        /**
         * Returns {@code true} when bit 6 ({@code 0x40}) is set, indicating that the card uses
         * a well-known pseudo-random card challenge algorithm. In this mode the expected card
         * challenge is derived as the first 6 bytes of a Retail MAC over the Security Domain AID
         * using {@code S-MAC}, and is verified before proceeding with EXTERNAL AUTHENTICATE.
         */
        private boolean wellKnownCardChallengeAlgorithm() {
            return (value & 0x40) != 0;
        }

        private String hexValue() {
            return Integer.toHexString(value);
        }
    }
}
