package app.aoki.cinpo.gp.scp;

import static app.aoki.cinpo.apdu.ApduUtil.assertSwOk;
import static app.aoki.cinpo.util.Util.concat;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.gp.GpCommands;
import app.aoki.cinpo.gp.GpCrypto;
import app.aoki.cinpo.gp.GpUtil;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Implements Secure Channel Protocol '03' (SCP03) as defined in GlobalPlatform
 * Amendment D v1.1.2 ({@code GPC_2.3_D_SCP03_v1.1.2}).
 *
 * <p>This class is the SCP03 strategy used by the secure-channel framework and implements
 * {@link InternalSecureChannelProtocol}. Each instance represents one Secure Channel Session;
 * session state (session keys, MAC chaining value, encryption counter) is held in instance fields
 * and reset on {@link #close()}.
 *
 * <h2>Supported security levels ([Amd D] §5.1, Table 5-1 / §7.1.2 Table 7-6)</h2>
 * <ul>
 *   <li>{@code 0x01} – C-MAC only</li>
 *   <li>{@code 0x03} – C-DECRYPTION + C-MAC</li>
 *   <li>{@code 0x11} – C-MAC + R-MAC</li>
 *   <li>{@code 0x13} – C-DECRYPTION + C-MAC + R-MAC</li>
 *   <li>{@code 0x33} – C-DECRYPTION + R-ENCRYPTION + C-MAC + R-MAC</li>
 * </ul>
 *
 * <h2>Cryptographic primitives</h2>
 * All AES operations (AES-CBC, AES-CMAC) are delegated to {@link GpCrypto}.
 * Key derivation uses KDF in counter mode with CMAC as the PRF per NIST SP 800-108 ([Amd D] §4.1.5).
 *
 * @see InternalSecureChannelProtocol
 */
final class Scp03Protocol implements InternalSecureChannelProtocol {

    private final SecureChannelProfile profile;

    /**
     * Session Secure Channel Encryption Key (S-ENC).
     *
     * <p>Derived from the static {@code Key-ENC} using derivation constant {@code 0x04}
     * per [Amd D] §6.2.1 Table 6-2. Used for C-DECRYPTION (command data encryption,
     * §6.2.6) and R-ENCRYPTION (response data decryption, §6.2.7), and also for generating
     * the ICV for each direction by encrypting the {@link #commandEncryptionCounter} block
     * with AES-CBC.
     *
     * <p>Key length matches the static key length: 16 bytes (AES-128), 24 bytes (AES-192),
     * or 32 bytes (AES-256). Null until {@link #authenticate} completes successfully.
     */
    private byte[] sEnc;

    /**
     * Secure Channel MAC Key for commands (S-MAC).
     *
     * <p>Derived from the static {@code Key-MAC} using derivation constant {@code 0x06}
     * per [Amd D] §6.2.1 Table 6-2. Used for C-MAC generation (§6.2.4) and for computing
     * both the card cryptogram ({@code 0x00}) and host cryptogram ({@code 0x01}) during
     * mutual authentication (§6.2.2).
     *
     * <p>Null until {@link #authenticate} completes successfully.
     */
    private byte[] sMac;

    /**
     * Secure Channel MAC Key for responses (S-RMAC).
     *
     * <p>Derived from the static {@code Key-MAC} using derivation constant {@code 0x07}
     * per [Amd D] §6.2.1 Table 6-2. Only present (non-null) when the "i" parameter
     * returned by the card advertises R-MAC support (bit b2 of "i", [Amd D] §5.1 Table 5-1).
     * Used for R-MAC verification (§6.2.5).
     *
     * <p>Null until {@link #authenticate} completes successfully.
     */
    private byte[] sRmac;

    /**
     * Current 16-byte MAC chaining value.
     *
     * <p>Initialized to 16 bytes of {@code 0x00} before the EXTERNAL AUTHENTICATE command
     * per [Amd D] §6.2.3: "For the EXTERNAL AUTHENTICATE command MAC verification, the
     * 'MAC chaining value' is set to 16 bytes [of 0x00]."
     *
     * <p>After each wrapped command the full 16-byte CMAC output (not just the 8-byte
     * truncated C-MAC) becomes the next MAC chaining value, so that command and response MACs
     * are chained: "Once the cryptograms are successfully verified, the full 16-byte C-MAC of
     * the previous command becomes the 'MAC chaining value' for the subsequent C-MAC
     * verification / R-MAC generation." ([Amd D] §6.2.3)
     *
     * <p>Null until {@link #authenticate} completes successfully.
     */
    private byte[] macChainingValue;

    /**
     * Monotonically increasing encryption counter used to generate unique ICVs.
     *
     * <p>Per [Amd D] §6.2.6: "The encryption counter's start value shall be set to 1 for the
     * first command following a successful EXTERNAL AUTHENTICATE command." The counter is
     * left-padded with zeroes to form a full 16-byte block; that block is then encrypted with
     * S-ENC to produce the ICV for AES-CBC command encryption (C-DECRYPTION) or, with the
     * most significant byte forced to {@code 0x80}, the ICV for response decryption
     * (R-ENCRYPTION, §6.2.7). The counter is incremented after every wrapped command (even
     * when no command data is present and encryption is skipped).
     *
     * <p>Value 0 before {@link #authenticate} completes; incremented by
     * {@link #advanceCommandState} after each successful {@link #transmit}.
     */
    private long commandEncryptionCounter;

    private boolean authenticated;
    private boolean rmacActive;
    private boolean rEncryptionActive;

    Scp03Protocol(SecureChannelProfile profile) {
        this.profile = profile;
    }

    /**
     * Performs SCP03 mutual authentication with the card (Figure 5-1, [Amd D] §5.2).
     *
     * <p>The authentication flow consists of the following steps:
     * <ol>
     *   <li><b>SELECT</b> – selects the Security Domain identified by
     *       {@link SecureChannelProfile#securityDomainAid()}.</li>
     *   <li><b>INITIALIZE UPDATE</b> ([Amd D] §7.1.1) – sends the 8-byte host challenge to
     *       the card. The card generates its own card challenge, derives session keys, and
     *       returns its card cryptogram along with the SCP identifier and the "i" parameter.</li>
     *   <li><b>Validate "i" parameter</b> – the card's "i" parameter bitmap ([Amd D] §5.1
     *       Table 5-1) is checked against the requested security level.</li>
     *   <li><b>Session-key derivation</b> ([Amd D] §6.2.1 Table 6-2) using
     *       {@link #kdfCounterModeCmac}:
     *       <ul>
     *         <li>S-ENC ← KDF(Key-ENC, {@code 0x04}, keyBitLen, context)</li>
     *         <li>S-MAC ← KDF(Key-MAC, {@code 0x06}, keyBitLen, context)</li>
     *         <li>S-RMAC ← KDF(Key-MAC, {@code 0x07}, keyBitLen, context)</li>
     *       </ul>
     *       where {@code context = hostChallenge || cardChallenge} (16 bytes).</li>
     *   <li><b>Card cryptogram verification</b> ([Amd D] §6.2.2) – expected card cryptogram
     *       = KDF(S-MAC, {@code 0x00}, 64, context) (8 bytes). Throws if mismatch.</li>
     *   <li><b>MAC chaining value initialization</b> – set to 16 × {@code 0x00} per
     *       [Amd D] §6.2.3.</li>
     *   <li><b>EXTERNAL AUTHENTICATE</b> ([Amd D] §7.1.2) – host cryptogram
     *       = KDF(S-MAC, {@code 0x01}, 64, context), wrapped with C-MAC (no C-DECRYPTION
     *       for this command regardless of the negotiated security level).</li>
     *   <li><b>Session state activation</b> – advances the MAC chaining value and marks the
     *       channel as authenticated.</li>
     * </ol>
     *
     * @param channel the raw APDU transport channel
     * @throws IllegalStateException if any cryptographic check fails or the card returns an
     *                               error status word
     */
    @Override
    public void authenticate(ApduChannel channel) {
        try {
            assertSwOk(channel.transmit(Iso7816Commands.selectDf(profile.securityDomainAid())));

            ResponseApdu initializeResponse = assertSwOk(channel.transmit(GpCommands.initializeUpdate(
                    profile.keyVersionNumber(),
                    0x00, // [Amd D] §7.1.1.4: Key Identifier (P2) shall always be '00' for SCP03
                    profile.hostChallenge())));

            InitializeUpdateResponse parsed = InitializeUpdateResponse.parse(initializeResponse.data());
            if (parsed.scpIdentifier() != 0x03) {
                throw new IllegalStateException("Card did not return SCP03 in INITIALIZE UPDATE response");
            }
            // [Amd D] §5.1 Table 5-1: validate the card's "i" parameter against the requested
            // security level so that mismatches are caught early with a clear error message
            // rather than failing silently later.
            validateIParameter(parsed.iParameter(), profile.securityLevel());

            // context = hostChallenge || cardChallenge (16 bytes total) per [Amd D] §6.2.1
            byte[] hostCardChallenge = concat(profile.hostChallenge(), parsed.cardChallenge());
            int keyBitLength = profile.encKey().length * 8;
            // [Amd D] §6.2.1 Table 6-2: derive S-ENC (const 0x04), S-MAC (0x06), S-RMAC (0x07)
            sEnc = kdfCounterModeCmac(profile.encKey(), 0x04, keyBitLength, hostCardChallenge);
            sMac = kdfCounterModeCmac(profile.macKey(), 0x06, keyBitLength, hostCardChallenge);
            sRmac = kdfCounterModeCmac(profile.macKey(), 0x07, keyBitLength, hostCardChallenge);
            // [Amd D] §6.2.2: card cryptogram = KDF(S-MAC, 0x00, 64, context)
            byte[] expectedCardCryptogram = kdfCounterModeCmac(
                    sMac,
                    0x00,
                    64,
                    hostCardChallenge);
            if (!MessageDigest.isEqual(expectedCardCryptogram, parsed.cardCryptogram())) {
                throw new IllegalStateException("SCP03 card cryptogram verification failed");
            }

            // [Amd D] §6.2.3: initial MAC chaining value for EXTERNAL AUTHENTICATE is 16 × 0x00
            macChainingValue = new byte[16];
            commandEncryptionCounter = 0L;

            // [Amd D] §6.2.2: host cryptogram = KDF(S-MAC, 0x01, 64, context)
            byte[] hostCryptogram = kdfCounterModeCmac(
                    sMac,
                    0x01,
                    64,
                    hostCardChallenge);
            CommandApdu capdu = GpCommands.externalAuthenticate(profile.securityLevel(), hostCryptogram);
            // EXTERNAL AUTHENTICATE is always wrapped with C-MAC only – no C-DECRYPTION
            // even when the negotiated security level includes C-DECRYPTION ([Amd D] §7.1.2).
            SecureWrappedCommand externalAuthenticate = wrapCommand(capdu, false);
            assertSwOk(channel.transmit(externalAuthenticate.command()));

            advanceCommandState(externalAuthenticate.nextMacChain());
            authenticated = true;
            rmacActive = GpUtil.usesRMac(profile.securityLevel());
            rEncryptionActive = GpUtil.usesREncryption(profile.securityLevel());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SCP03 cryptographic setup failed", e);
        }
    }

    /**
     * Wraps {@code capdu} with the current session's secure messaging, transmits it through
     * {@code channel}, unwraps and verifies the response, then advances the MAC chaining value
     * and encryption counter.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Call {@link #wrapCommand} to apply C-MAC (and C-DECRYPTION if the security level
     *       requires it).</li>
     *   <li>Transmit the wrapped APDU.</li>
     *   <li>Call {@link #unwrapResponse} to verify R-MAC and decrypt R-ENCRYPTION data.</li>
     *   <li>Advance {@link #macChainingValue} and {@link #commandEncryptionCounter} via
     *       {@link #advanceCommandState}.</li>
     * </ol>
     *
     * @param channel the raw APDU transport channel
     * @param capdu   the plaintext command APDU
     * @return the verified, decrypted response APDU
     * @throws IllegalStateException if the channel is not yet authenticated, if R-MAC
     *                               verification fails, or if a cryptographic error occurs
     */
    @Override
    public ResponseApdu transmit(ApduChannel channel, CommandApdu capdu) {
        if (!authenticated) {
            throw new IllegalStateException("SCP03 secure channel is not authenticated");
        }

        try {
            SecureWrappedCommand wrapped = wrapCommand(capdu);

            ResponseApdu response = channel.transmit(wrapped.command());
            ResponseApdu unwrapped = unwrapResponse(response, wrapped.nextMacChain(), commandEncryptionCounter);
            advanceCommandState(wrapped.nextMacChain());
            return unwrapped;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SCP03 secure messaging failed", e);
        }
    }

    @Override
    public void close() {
        resetSessionState();
    }

    /**
     * Verifies the R-MAC and, if R-ENCRYPTION is active, decrypts the response data.
     *
     * <h3>R-MAC verification ([Amd D] §6.2.5)</h3>
     * <p>R-MAC is skipped entirely when the response status word indicates an error other than
     * {@code 9000}, {@code 62xx}, or {@code 63xx}: "No R-MAC shall be generated and no
     * protection shall be applied to a response that includes an error status word" ([Amd D]
     * §6.2.5). The EXTERNAL AUTHENTICATE command/response also never carries an R-MAC
     * ([Amd D] §6.2.5, note).
     *
     * <p>When R-MAC is expected the last 8 bytes of the response body are the R-MAC. The
     * expected value is:
     * <pre>
     *   R-MAC = first 8 bytes of CMAC(S-RMAC, macChainingValue || responseData || SW1 || SW2)
     * </pre>
     * where {@code macChainingValue} is the full 16-byte CMAC output of the just-sent
     * command ([Amd D] §6.2.5 Figure 6-2).
     *
     * <h3>R-ENCRYPTION decryption ([Amd D] §6.2.7)</h3>
     * <p>When R-ENCRYPTION is active and the verified response data field is non-empty:
     * <ol>
     *   <li>Build the counter block for {@code encryptionCounter} (same block used for
     *       C-DECRYPTION) but set its most significant byte to {@code 0x80}.</li>
     *   <li>ICV = AES-CBC-encrypt(S-ENC, modifiedCounterBlock) – single block.</li>
     *   <li>Decrypt: AES-CBC-decrypt(S-ENC, ICV, cipherData).</li>
     *   <li>Remove ISO 9797 Method 2 padding.</li>
     * </ol>
     *
     * @param response          the raw response received from the card
     * @param expectedMacChain  the full 16-byte CMAC output of the matching command APDU,
     *                          used as the MAC chaining value for R-MAC computation
     * @param encryptionCounter the current encryption counter value at the time the command
     *                          was sent (before {@link #advanceCommandState} is called)
     * @return a {@link ResponseApdu} with the verified, plaintext data field
     * @throws IllegalStateException if R-MAC verification fails or the response body is too
     *                               short to contain the 8-byte R-MAC
     */
    private ResponseApdu unwrapResponse(ResponseApdu response, byte[] expectedMacChain, long encryptionCounter)
            throws GeneralSecurityException {
        if (!rmacActive) {
            return response;
        }

        byte[] body = response.data();
        // [Amd D] §6.2.5: skip R-MAC for error SW words other than 9000, 62xx, 63xx.
        // Note: EXTERNAL AUTHENTICATE never returns R-MAC ([Amd D] §6.2.5).
        if (response.sw() != 0x9000 && response.sw1() != 0x62 && response.sw1() != 0x63) {
            return response;
        }
        if (body.length < 8) {
            throw new IllegalStateException("SCP03 response with R-MAC is shorter than 8 bytes");
        }

        byte[] responseData = Arrays.copyOf(body, body.length - 8);
        byte[] responseMac = Arrays.copyOfRange(body, body.length - 8, body.length);
        // [Amd D] §6.2.5 Figure 6-2: R-MAC = first 8 bytes of
        //   CMAC(S-RMAC, macChainingValue || responseData || SW1 || SW2)
        byte[] expectedResponseMac = Arrays.copyOf(
                GpCrypto.cmac(sRmac, concat(expectedMacChain, responseData, new byte[]{(byte) response.sw1(), (byte) response.sw2()})),
                8);
        if (!MessageDigest.isEqual(responseMac, expectedResponseMac)) {
            throw new IllegalStateException("SCP03 R-MAC verification failed");
        }

        if (rEncryptionActive && responseData.length > 0) {
            responseData = decryptResponseData(responseData, encryptionCounter);
        }
        return new ResponseApdu(responseData, response.sw1(), response.sw2());
    }

    private byte[] decryptResponseData(byte[] responseData, long encryptionCounter) throws GeneralSecurityException {
        byte[] counterBlock = buildCounterBlock(encryptionCounter, true);
        byte[] icv = GpCrypto.aesCbcEncryptSingleBlock(sEnc, counterBlock);
        byte[] padded = GpCrypto.aesCbcDecrypt(sEnc, icv, responseData);
        return GpCrypto.removeIso9797Method2Padding(padded);
    }

    private void advanceCommandState(byte[] nextMacChainingValue) {
        macChainingValue = nextMacChainingValue;
        commandEncryptionCounter += 1L;
    }

    private void resetSessionState() {
        authenticated = false;
        rmacActive = false;
        rEncryptionActive = false;
        commandEncryptionCounter = 0L;
        sEnc = null;
        sMac = null;
        sRmac = null;
        macChainingValue = null;
    }

    private SecureWrappedCommand wrapCommand(CommandApdu capdu) throws GeneralSecurityException {
        return wrapCommand(capdu, GpUtil.usesCDecryption(profile.securityLevel()));
    }

    /**
     * Applies SCP03 secure messaging to {@code capdu} and returns the wrapped command together
     * with the full 16-byte CMAC output that will become the next MAC chaining value.
     *
     * <h3>C-DECRYPTION ([Amd D] §6.2.6)</h3>
     * <p>When {@code encryptData} is {@code true} and the command has a non-empty data field:
     * <ol>
     *   <li>Pad the plaintext data with ISO 9797 Method 2 padding to a 16-byte boundary
     *       ([Amd D] §4.1.4).</li>
     *   <li>Counter block = {@link #commandEncryptionCounter} value left-padded with zeroes to
     *       16 bytes.</li>
     *   <li>ICV = AES-CBC-encrypt(S-ENC, counterBlock) – single block.</li>
     *   <li>Ciphertext = AES-CBC-encrypt(S-ENC, ICV, paddedData).</li>
     * </ol>
     * The encryption counter is incremented by {@link #advanceCommandState} after this method
     * returns; it is <em>not</em> incremented here.
     *
     * <h3>C-MAC ([Amd D] §6.2.4 Figure 6-1)</h3>
     * <p>CMAC is computed over:
     * <pre>
     *   macChainingValue (16 bytes) || header (5 bytes) || protectedData
     * </pre>
     * where {@code header = secureCLA_channelZeroed || INS || P1 || P2 || (Lc+8)}.
     * <ul>
     *   <li>The class byte used for the CMAC input must have its logical channel bits zeroed
     *       per [Amd D] §6.2.4.</li>
     *   <li>{@code Lc} is incremented by 8 to account for the 8-byte C-MAC appended to the
     *       data field ([Amd D] §6.2.4).</li>
     * </ul>
     * The full 16-byte CMAC output is stored as {@link SecureWrappedCommand#nextMacChain}; only
     * the first 8 bytes are included in the transmitted APDU as the C-MAC.
     *
     * <h3>Final APDU</h3>
     * <p>The final command APDU uses the secure-messaging CLA byte (with the actual logical
     * channel restored) per [Amd D] §6.2.4 and carries {@code protectedData || C-MAC} as its
     * data field.
     *
     * @param capdu       the plaintext command APDU
     * @param encryptData {@code true} to apply C-DECRYPTION to the command data field
     * @return the wrapped command and the next MAC chaining value
     */
    private SecureWrappedCommand wrapCommand(CommandApdu capdu, boolean encryptData)
            throws GeneralSecurityException {
        byte[] plainData = capdu.data() == null ? new byte[0] : Arrays.copyOf(capdu.data(), capdu.data().length);
        byte[] protectedData = plainData;
        if (encryptData && plainData.length > 0) {
            protectedData = encryptCommandData(sEnc, plainData, commandEncryptionCounter);
        }

        int secureCla = GpUtil.secureMessagingCla(capdu.cla());
        // [Amd D] §6.2.4: the C-MAC input header must use a channel-zero class byte.
        // For first interindustry (b7=0) the channel occupies b1-b2; for further interindustry
        // (b7=1, channels 4–19) the channel occupies b1-b4.
        int macCla = zeroChannelInCla(secureCla);
        int effectiveLc = protectedData.length + 8;
        byte[] header = new byte[]{
            (byte) macCla,
            (byte) capdu.ins(),
            (byte) capdu.p1(),
            (byte) capdu.p2(),
            (byte) effectiveLc
        };
        // [Amd D] §6.2.4 Figure 6-1: full CMAC = CMAC(S-MAC, macChainingValue || header || data)
        byte[] fullMac = GpCrypto.cmac(sMac, concat(macChainingValue, header, protectedData));
        byte[] commandMac = Arrays.copyOf(fullMac, 8);
        byte[] finalData = concat(protectedData, commandMac);
        // Final APDU carries secureCla (with the real channel number) per [Amd D] §6.2.4.
        CommandApdu command = capdu.hasLe()
                ? new CommandApdu(secureCla, capdu.ins(), capdu.p1(), capdu.p2(), finalData, 256)
                : new CommandApdu(secureCla, capdu.ins(), capdu.p1(), capdu.p2(), finalData);
        return new SecureWrappedCommand(command, fullMac);
    }

    private static byte[] encryptCommandData(byte[] sessionEncKey, byte[] plainData, long encryptionCounter)
            throws GeneralSecurityException {
        byte[] padded = GpCrypto.padIso9797Method2(plainData, 16);
        byte[] counterBlock = buildCounterBlock(encryptionCounter, false);
        byte[] icv = GpCrypto.aesCbcEncryptSingleBlock(sessionEncKey, counterBlock);
        return GpCrypto.aesCbcEncrypt(sessionEncKey, icv, padded);
    }

    /**
     * Returns a version of {@code secureCla} with the logical channel bits zeroed.
     *
     * <p>[Amd D] §6.2.4: the C-MAC input header must use a channel-zero class byte.
     * For first interindustry (b7=0) the channel occupies b1-b2; for further interindustry
     * (b7=1, channels 4–19) the channel occupies b1-b4.
     */
    private static int zeroChannelInCla(int secureCla) {
        if ((secureCla & 0x40) != 0) {
            // Further interindustry class byte: b1-b4 encode the channel number (0x0F mask).
            return secureCla & 0xF0;
        }
        // First interindustry class byte: b1-b2 encode the channel number (0x03 mask).
        return secureCla & 0xFC;
    }

    /**
     * Builds the 16-byte AES-CBC counter block used as the ICV source for C-DECRYPTION
     * and R-ENCRYPTION.
     *
     * <p>Per [Amd D] §6.2.6: "The encryption counter's binary value shall be left padded with
     * zeroes to form a full block." The resulting 16-byte big-endian block is then encrypted
     * with S-ENC (single AES-CBC block) to produce the ICV.
     *
     * <p>For R-ENCRYPTION ([Amd D] §6.2.7), the same counter value is used but with an
     * additional step: "Before encryption, the most significant byte of this block shall be
     * set to {@code '80'}." This ensures the R-ENCRYPTION ICV is always different from the
     * C-DECRYPTION ICV for the same counter value.
     *
     * @param value        the current {@link #commandEncryptionCounter} value
     * @param responseMode {@code true} to set the most significant byte to {@code 0x80}
     *                     (R-ENCRYPTION, §6.2.7); {@code false} for C-DECRYPTION (§6.2.6)
     * @return a 16-byte counter block, big-endian, with the counter right-aligned
     */
    private static byte[] buildCounterBlock(long value, boolean responseMode) {
        byte[] block = new byte[16];
        long current = value;
        for (int i = 15; i >= 0 && current != 0L; i--) {
            block[i] = (byte) (current & 0xFF);
            current >>>= 8;
        }
        if (responseMode) {
            block[0] = (byte) 0x80;
        }
        return block;
    }

    /**
     * Validates the SCP03 "i" parameter returned in the INITIALIZE UPDATE response against the
     * security level that was requested in the session profile.
     *
     * <p>[Amd D] Table 5-1: bits b2-b3 of the "i" parameter advertise the card's R-MAC and
     * R-ENCRYPTION support:
     * <ul>
     *   <li>{@code 00} – no R-MAC/R-ENCRYPTION support</li>
     *   <li>{@code 01} – R-MAC supported, R-ENCRYPTION not supported</li>
     *   <li>{@code 11} – R-MAC and R-ENCRYPTION both supported</li>
     * </ul>
     */
    private static void validateIParameter(int iParameter, int securityLevel) {
        boolean rmacRequested = GpUtil.usesRMac(securityLevel);
        boolean rEncRequested = GpUtil.usesREncryption(securityLevel);
        // b2 of "i" = R-MAC support bit (0x02); b3 = R-ENCRYPTION support bit (0x04)
        boolean rmacAdvertised = (iParameter & 0x02) != 0;
        boolean rEncAdvertised = (iParameter & 0x04) != 0;

        if (rmacRequested && !rmacAdvertised) {
            throw new IllegalStateException(String.format(
                    "Security level requests R-MAC but card \"i\" parameter (0x%02X) does not advertise R-MAC support",
                    iParameter));
        }
        if (rEncRequested && !rEncAdvertised) {
            throw new IllegalStateException(String.format(
                    "Security level requests R-ENCRYPTION but card \"i\" parameter (0x%02X) does not advertise R-ENCRYPTION support",
                    iParameter));
        }
    }

    /**
     * KDF in counter mode using AES-CMAC as the PRF, per [Amd D] §4.1.5 and NIST SP 800-108.
     *
     * <p>Each CMAC invocation operates on the following concatenated input ("fixed input data"
     * plus iteration counter, [Amd D] §4.1.5):
     * <pre>
     *   label (12 bytes) || 0x00 (1 byte) || L (2 bytes) || i (1 byte) || context
     * </pre>
     * where:
     * <ul>
     *   <li><b>label</b> – 12 bytes: 11 bytes of {@code 0x00} followed by the 1-byte
     *       {@code derivationConstant} at position {@code [11]} (see Table 4-1:
     *       {@code 0x00}=card cryptogram, {@code 0x01}=host cryptogram, {@code 0x04}=S-ENC,
     *       {@code 0x06}=S-MAC, {@code 0x07}=S-RMAC).</li>
     *   <li><b>separation indicator</b> – fixed {@code 0x00}.</li>
     *   <li><b>L</b> – 2-byte big-endian output bit length: {@code 0x0040} (64 bits, for
     *       8-byte cryptograms), {@code 0x0080} (128 bits, AES-128 keys), {@code 0x00C0}
     *       (192 bits, AES-192 keys), or {@code 0x0100} (256 bits, AES-256 keys).</li>
     *   <li><b>i</b> – 1-byte iteration counter ({@code 0x01} or {@code 0x02}); {@code 0x02}
     *       is used when {@code L} is {@code 0x00C0} or {@code 0x0100}, i.e. when the CMAC
     *       must be called twice to produce enough output.</li>
     *   <li><b>context</b> – caller-supplied data; for session keys this is
     *       {@code hostChallenge || cardChallenge} (16 bytes, [Amd D] §6.2.1).</li>
     * </ul>
     *
     * @param key                 the AES key used as the CMAC key (Key-ENC, Key-MAC, or S-MAC)
     * @param derivationConstant  1-byte constant from Table 4-1 placed at label byte {@code [11]}
     * @param outputBitLength     desired output size in bits; must be 64, 128, 192, or 256
     * @param context             the KDF context bytes (e.g. {@code hostChallenge || cardChallenge})
     * @return the derived key or cryptogram ({@code outputBitLength / 8} bytes)
     * @throws IllegalArgumentException if {@code outputBitLength} is not one of the four
     *                                  allowed values per [Amd D] §4.1.5
     */
    private static byte[] kdfCounterModeCmac(byte[] key, int derivationConstant, int outputBitLength,
            byte[] context) throws GeneralSecurityException {
        // [Amd D] §4.1.5: L may only be 0x0040 (64), 0x0080 (128), 0x00C0 (192), or 0x0100 (256).
        if (outputBitLength != 64 && outputBitLength != 128 && outputBitLength != 192 && outputBitLength != 256) {
            throw new IllegalArgumentException(
                    "outputBitLength must be 64, 128, 192, or 256 per [Amd D] §4.1.5; got: " + outputBitLength);
        }
        if ((outputBitLength & 0x07) != 0) {
            throw new IllegalArgumentException("outputBitLength must be byte aligned");
        }
        int outputLengthBytes = outputBitLength / 8;
        byte[] label = new byte[12];
        label[11] = (byte) derivationConstant;
        byte[] result = new byte[outputLengthBytes];
        int offset = 0;
        int counter = 1;
        while (offset < outputLengthBytes) {
            byte[] input = concat(
                    label,
                    new byte[]{0x00},
                    new byte[]{(byte) (outputBitLength >>> 8), (byte) outputBitLength},
                    new byte[]{(byte) counter},
                    context);
            byte[] block = GpCrypto.cmac(key, input);
            int remaining = Math.min(block.length, outputLengthBytes - offset);
            System.arraycopy(block, 0, result, offset, remaining);
            offset += remaining;
            counter += 1;
        }
        return result;
    }

    private record SecureWrappedCommand(CommandApdu command, byte[] nextMacChain) {

    }

    /**
     * Parsed representation of a successful INITIALIZE UPDATE response ([Amd D] §7.1.1).
     *
     * <p>The 29-byte (random challenge) or 32-byte (pseudo-random challenge) response data
     * field is structured as follows per [Amd D] Table 7-3:
     * <ul>
     *   <li>bytes {@code [0..9]}   – key diversification data (10 bytes): typically used by
     *       a backend system to derive the card's static keys.</li>
     *   <li>byte  {@code [10]}     – Key Version Number.</li>
     *   <li>byte  {@code [11]}     – SCP identifier ({@link #scpIdentifier}); shall be
     *       {@code 0x03} for SCP03.</li>
     *   <li>byte  {@code [12]}     – "i" parameter ({@link #iParameter}); bitmap per
     *       [Amd D] §5.1 Table 5-1 advertising challenge type and R-MAC/R-ENCRYPTION support.</li>
     *   <li>bytes {@code [13..20]} – card challenge ({@link #cardChallenge}, 8 bytes):
     *       random or pseudo-random value unique to this session.</li>
     *   <li>bytes {@code [21..28]} – card cryptogram ({@link #cardCryptogram}, 8 bytes):
     *       authentication cryptogram computed by the card using S-MAC and the KDF.</li>
     *   <li>bytes {@code [29..31]} – sequence counter (3 bytes, conditional): only present
     *       when SCP03 is configured for pseudo-random challenge generation.</li>
     * </ul>
     *
     * @param scpIdentifier  byte {@code [11]} – Secure Channel Protocol identifier (expected
     *                       {@code 0x03})
     * @param iParameter     byte {@code [12]} – "i" parameter bitmap ([Amd D] §5.1 Table 5-1)
     * @param cardChallenge  bytes {@code [13..20]} – 8-byte card challenge
     * @param cardCryptogram bytes {@code [21..28]} – 8-byte card authentication cryptogram
     */
    private record InitializeUpdateResponse(
            int scpIdentifier,
            int iParameter,
            byte[] cardChallenge,
            byte[] cardCryptogram) {

        /**
         * Parses the INITIALIZE UPDATE response data.
         *
         * <p>[Amd D] Table 7-3 response structure (29 bytes for random challenge, 32 for pseudo-random):
         * <ul>
         *   <li>bytes 0–9: key diversification data (10 bytes)</li>
         *   <li>byte 10: Key Version Number</li>
         *   <li>byte 11: SCP identifier (shall be 0x03)</li>
         *   <li>byte 12: "i" parameter (Table 5-1)</li>
         *   <li>bytes 13–20: card challenge (8 bytes)</li>
         *   <li>bytes 21–28: card cryptogram (8 bytes)</li>
         *   <li>bytes 29–31: sequence counter (3 bytes, conditional – pseudo-random mode only)</li>
         * </ul>
         */
        private static InitializeUpdateResponse parse(byte[] data) {
            if (data.length != 29 && data.length != 32) {
                throw new IllegalArgumentException("Unsupported SCP03 INITIALIZE UPDATE response length: " + data.length);
            }
            int scpIdentifier = data[11] & 0xFF;
            int iParameter = data[12] & 0xFF;
            byte[] cardChallenge = Arrays.copyOfRange(data, 13, 21);
            byte[] cardCryptogram = Arrays.copyOfRange(data, 21, 29);
            return new InitializeUpdateResponse(scpIdentifier, iParameter, cardChallenge, cardCryptogram);
        }
    }
}
