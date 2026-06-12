package app.aoki.cinpo.gp;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Raw cryptographic primitives used by the SCP02 and SCP03 secure channel protocols.
 *
 * <p>This class consolidates every cipher operation in one place so that all
 * cryptographic code is visible in a single, auditable unit.  Protocol-level
 * logic — session-key derivation, cryptogram generation, command/response
 * wrapping — lives in {@link Scp02Protocol} and {@link Scp03Protocol}
 * respectively; only the underlying block-cipher and MAC primitives belong here.
 *
 * <h2>SCP03 (AES)</h2>
 * <ul>
 *   <li>Encryption/decryption uses AES-CBC as specified in NIST SP 800-38A
 *       (GlobalPlatform Card Specification §B.2.1).</li>
 *   <li>MAC calculation uses AES-CMAC as specified in NIST SP 800-38B
 *       (GPCS §B.2.2).  The raw CMAC output is 16 bytes; callers truncate
 *       to 8 bytes for C-MAC / R-MAC per SCP03 Amendment D §6.2.4.</li>
 *   <li>Padding uses ISO/IEC 9797-1 Method 2: append {@code 0x80} then
 *       {@code 0x00…} to the next 16-byte boundary (GPCS §B.2.3).</li>
 * </ul>
 *
 * <h2>SCP02 (3DES)</h2>
 * <ul>
 *   <li>Full-3DES CBC MAC is used for card/host cryptogram generation.</li>
 *   <li>Retail MAC (ISO 9797-1 Algorithm 3) is used for C-MAC integrity.</li>
 *   <li>2-key 3DES keys (16 bytes) are expanded to 3-key format (24 bytes)
 *       as {@code K1 ‖ K2 ‖ K1}.</li>
 * </ul>
 */
public final class GpCrypto {

    private static final byte[] ZERO_IV_8 = new byte[8];
    private static final byte[] ZERO_IV_16 = new byte[16];

    private GpCrypto() {
    }

    // ─── AES primitives (SCP03) ──────────────────────────────────────────

    /**
     * Encrypts a single AES block using ECB mode (no padding).
     *
     * <p>This is the fundamental AES building block used by {@link #cmac} for
     * subkey generation and CBC-MAC finalisation, and by
     * {@link #aesCbcEncryptSingleBlock} for ICV derivation.
     *
     * @param key   AES key (16, 24, or 32 bytes)
     * @param block exactly one AES block (16 bytes)
     * @return 16-byte ciphertext block
     */
    public static byte[] aesEcbEncrypt(byte[] key, byte[] block) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(block);
    }

    /**
     * Encrypts {@code message} using AES-CBC mode (no padding).
     *
     * <p>Used for command-data encryption (C-DECRYPTION) and response-data
     * encryption (R-ENCRYPTION) as specified in SCP03 Amendment D §6.2.6 /
     * §6.2.7 and GPCS §B.2.1.  The ICV for each APDU is derived from an
     * AES-ECB encryption of the left-zero-padded command counter (see
     * {@link #aesCbcEncryptSingleBlock}).
     *
     * <p>The caller is responsible for ISO 9797 Method 2 padding of
     * {@code message} before this call (GPCS §B.2.3).
     *
     * @param key     AES key (16, 24, or 32 bytes)
     * @param iv      initialisation vector (16 bytes)
     * @param message plaintext; length must be a non-zero multiple of 16
     * @return ciphertext of the same length as {@code message}
     */
    public static byte[] aesCbcEncrypt(byte[] key, byte[] iv, byte[] message) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(message);
    }

    /**
     * Encrypts a single 16-byte block with a zero IV using AES-CBC.
     *
     * <p>Used to produce the ICV for command encryption: the command-counter
     * value is left-padded with zeroes to form a 16-byte block, then this
     * method encrypts it with S-ENC to yield an unpredictable ICV, satisfying
     * the NIST SP 800-38A requirements cited in SCP03 Amendment D §6.2.6.
     *
     * @param key   S-ENC session key (16 bytes)
     * @param block counter block (16 bytes, counter left-padded with {@code 0x00})
     * @return 16-byte ICV
     */
    public static byte[] aesCbcEncryptSingleBlock(byte[] key, byte[] block) throws GeneralSecurityException {
        return aesCbcEncrypt(key, ZERO_IV_16, block);
    }

    /**
     * Decrypts {@code cipherText} using AES-CBC mode (no padding).
     *
     * <p>Used by the off-card entity to decrypt response data fields protected
     * under R-ENCRYPTION (SCP03 Amendment D §6.2.7, GPCS §B.2.1).  The ICV
     * is derived the same way as for command encryption but with the most
     * significant byte of the counter block set to {@code 0x80} before
     * encryption with S-ENC.
     *
     * @param key        AES key (16, 24, or 32 bytes)
     * @param iv         initialisation vector (16 bytes)
     * @param cipherText ciphertext; length must be a non-zero multiple of 16
     * @return plaintext of the same length as {@code cipherText}
     */
    public static byte[] aesCbcDecrypt(byte[] key, byte[] iv, byte[] cipherText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(cipherText);
    }

    // ─── AES-CMAC (RFC 4493 / NIST SP 800-38B) ──────────────────────────

    /**
     * Computes a full 16-byte AES-CMAC over {@code message}.
     *
     * <p>Implements the CMAC construction defined in NIST SP 800-38B and
     * cited in GlobalPlatform Card Specification §B.2.2:
     * <blockquote>
     * "CMAC as specified in NIST SP 800-38B is used for MAC calculations.
     *  The resulting signature is composed of 16 bytes."
     * </blockquote>
     *
     * <h3>Algorithm summary</h3>
     * <ol>
     *   <li><b>Subkey generation</b>: Encrypt an all-zero block with {@code key}
     *       to obtain {@code L}.  Derive {@code K1 = double(L)} and
     *       {@code K2 = double(K1)} using the GF(2¹²⁸) doubling operation
     *       ({@link #doubleLu}), where the reduction polynomial uses
     *       {@code Rb = 0x87}.</li>
     *   <li><b>CBC pass</b>: XOR-then-AES-ECB-encrypt each complete block
     *       in sequence, starting from an all-zero state.</li>
     *   <li><b>Final block</b>:
     *     <ul>
     *       <li>If the message length is a non-zero multiple of 16 (complete
     *           block): XOR the last block with {@code K1} before the final
     *           AES-ECB step.</li>
     *       <li>Otherwise (incomplete or empty): append {@code 0x80 00…00}
     *           padding (ISO/IEC 9797-1 Method 2 per GPCS §B.2.3) then XOR
     *           with {@code K2}.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p><b>Note:</b> This method returns the full 16-byte CMAC output.
     * Callers must truncate to 8 bytes (most significant) for C-MAC and
     * R-MAC as required by SCP03 Amendment D §6.2.4:
     * <blockquote>
     * "The Secure channel shall support a MAC of 8 bytes length (even if the
     *  AES block length is 16 bytes). Hence the eight most significant bytes
     *  are considered."
     * </blockquote>
     *
     * <p>C-MAC input is: {@code MAC_chaining_value (16 bytes) ‖ modified_header
     * (5 bytes) ‖ command_data_field} (Amd D §6.2.4, Figure 6-1).
     * R-MAC input is: {@code MAC_chaining_value ‖ response_data_field ‖ SW}
     * (Amd D §6.2.5, Figure 6-2).
     *
     * @param key     S-MAC or S-RMAC session key (16 bytes for AES-128)
     * @param message arbitrary-length input; may be empty
     * @return 16-byte CMAC tag
     */
    public static byte[] cmac(byte[] key, byte[] message) throws GeneralSecurityException {
        byte[] l = aesEcbEncrypt(key, new byte[16]);
        byte[] k1 = doubleLu(l);
        byte[] k2 = doubleLu(k1);

        int blockCount = Math.max(1, (message.length + 15) / 16);
        boolean completeFinalBlock = message.length != 0 && message.length % 16 == 0;

        byte[] state = new byte[16];
        for (int blockIndex = 0; blockIndex < blockCount - 1; blockIndex++) {
            byte[] block = Arrays.copyOfRange(message, blockIndex * 16, (blockIndex + 1) * 16);
            state = aesEcbEncrypt(key, xor(state, block));
        }

        byte[] lastBlock = new byte[16];
        int lastBlockOffset = (blockCount - 1) * 16;
        int remaining = message.length - lastBlockOffset;
        if (completeFinalBlock) {
            System.arraycopy(message, lastBlockOffset, lastBlock, 0, 16);
            lastBlock = xor(lastBlock, k1);
        } else {
            if (remaining > 0) {
                System.arraycopy(message, lastBlockOffset, lastBlock, 0, remaining);
            }
            lastBlock[remaining] = (byte) 0x80;
            lastBlock = xor(lastBlock, k2);
        }
        return aesEcbEncrypt(key, xor(state, lastBlock));
    }

    /**
     * Doubles a 128-bit value in GF(2¹²⁸) using the CMAC subkey-generation
     * operation defined in NIST SP 800-38B §6.1.
     *
     * <p>The operation is: left-shift {@code block} by one bit; if the
     * original most-significant bit was 1, XOR the result with the constant
     * {@code Rb = 0x00…0087} (the irreducible polynomial {@code x¹²⁸ + x⁷ +
     * x² + x + 1} reduced modulo 2, represented as {@code 0x87} in the
     * least-significant byte of a 128-bit word).
     *
     * @param block 16-byte input
     * @return 16-byte doubled value
     */
    private static byte[] doubleLu(byte[] block) {
        byte[] shifted = shiftLeftOneBit(block);
        if ((block[0] & 0x80) != 0) {
            shifted[15] ^= (byte) 0x87;
        }
        return shifted;
    }

    private static byte[] shiftLeftOneBit(byte[] bytes) {
        byte[] shifted = new byte[bytes.length];
        int carry = 0;
        for (int index = bytes.length - 1; index >= 0; index--) {
            int value = bytes[index] & 0xFF;
            shifted[index] = (byte) ((value << 1) | carry);
            carry = (value >>> 7) & 0x01;
        }
        return shifted;
    }

    // ─── 3DES primitives (SCP02) ────────────────────────────────────────

    /**
     * Encrypts {@code data} using Triple-DES in CBC mode (no padding).
     *
     * <p>Used for SCP02 cryptogram calculation where a fully expanded
     * 24-byte 3-key 3DES key is already available.  For MAC operations
     * prefer {@link #full3DesCbcMac} or {@link #retailMac} which handle
     * key expansion internally.
     *
     * @param key24 3-key 3DES key (24 bytes)
     * @param iv8   8-byte initialisation vector
     * @param data  plaintext; length must be a non-zero multiple of 8
     * @return ciphertext of the same length as {@code data}
     */
    public static byte[] des3CbcEncrypt(byte[] key24, byte[] iv8, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"), new IvParameterSpec(iv8));
        return cipher.doFinal(data);
    }

    /**
     * Computes a full 3DES CBC-MAC over {@code paddedData} using a zero ICV.
     *
     * <p>Runs 3DES-CBC over the entire padded input and returns the final
     * 8-byte block as the MAC.  Used in SCP02 for card/host cryptogram
     * computation where the entire message is processed with Triple-DES
     * (unlike {@link #retailMac} which applies single-DES to all but the
     * last block).
     *
     * @param key16      2-key 3DES key (16 bytes); expanded internally to 24 bytes
     * @param paddedData pre-padded input; length must be a non-zero multiple of 8
     * @return 8-byte MAC (last ciphertext block)
     */
    public static byte[] full3DesCbcMac(byte[] key16, byte[] paddedData) throws GeneralSecurityException {
        byte[] key24 = expand3DesKey(key16);
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"), new IvParameterSpec(ZERO_IV_8));
        return cipher.doFinal(paddedData);
    }

    /**
     * Computes an ISO 9797-1 Algorithm 3 (Retail MAC) over {@code paddedData}.
     *
     * <p>This is the C-MAC algorithm used by SCP02.  Algorithm 3 operates as
     * follows:
     * <ol>
     *   <li>Apply single-DES CBC encryption with the left half of the key
     *       ({@code K1 = key[0..7]}) across all blocks.</li>
     *   <li>Take the final 8-byte CBC output block.</li>
     *   <li>Decrypt that block with the right half of the key
     *       ({@code K2 = key[8..15]}) using single-DES ECB.</li>
     *   <li>Re-encrypt the result with {@code K1} using single-DES ECB.</li>
     * </ol>
     *
     * <p>This implements the "outer 3DES finalisation" step that distinguishes
     * ISO 9797-1 Algorithm 3 from a plain single-DES CBC-MAC.
     *
     * <p><b>Caveat:</b> Input must be pre-padded to a non-zero multiple of 8 bytes
     * before calling this method.  This is ISO 9797-1 Algorithm 3 (Retail MAC)
     * as used by SCP02.
     *
     * @param key16      2-key 3DES key (16 bytes = K1 ‖ K2)
     * @param icv        8-byte initial chaining value
     * @param paddedData pre-padded plaintext; must be a non-zero multiple of 8 bytes
     * @return 8-byte MAC
     * @throws IllegalArgumentException if {@code paddedData} is empty or not a
     *                                   multiple of 8 bytes
     */
    public static byte[] retailMac(byte[] key16, byte[] icv, byte[] paddedData) throws GeneralSecurityException {
        if (paddedData.length == 0 || (paddedData.length % 8) != 0) {
            throw new IllegalArgumentException("Retail MAC input must be padded to a non-zero multiple of 8 bytes");
        }
        byte[] leftKey = Arrays.copyOfRange(key16, 0, 8);
        byte[] rightKey = Arrays.copyOfRange(key16, 8, 16);
        byte[] current = Arrays.copyOf(icv, icv.length);
        Cipher desCbc = Cipher.getInstance("DES/CBC/NoPadding");
        desCbc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(leftKey, "DES"), new IvParameterSpec(icv));
        byte[] cbcOutput = desCbc.doFinal(paddedData);
        System.arraycopy(cbcOutput, cbcOutput.length - 8, current, 0, 8);

        Cipher desEcb = Cipher.getInstance("DES/ECB/NoPadding");
        desEcb.init(Cipher.DECRYPT_MODE, new SecretKeySpec(rightKey, "DES"));
        byte[] decrypted = desEcb.doFinal(current);
        desEcb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(leftKey, "DES"));
        return desEcb.doFinal(decrypted);
    }

    /**
     * Encrypts {@code data} using single-DES ECB mode (no padding).
     *
     * <p>Used in SCP02 to encrypt an ICV before it is used as the chaining
     * value for the next C-MAC computation, preventing chosen-plaintext
     * attacks on the DES CBC chain between successive APDU commands.
     *
     * @param key8 8-byte DES key
     * @param data plaintext; length must be a non-zero multiple of 8
     * @return ciphertext of the same length as {@code data}
     */
    public static byte[] desEcbEncrypt(byte[] key8, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key8, "DES"));
        return cipher.doFinal(data);
    }

    /**
     * Expands a 2-key 3DES key (16 bytes) to the 3-key format (24 bytes)
     * required by the Java {@code DESede} cipher provider.
     *
     * <p>The expansion replicates the first 8 bytes of the key as the third
     * key component: {@code K1 ‖ K2 ‖ K1} where {@code K1 = key[0..7]} and
     * {@code K2 = key[8..15]}.  This is the standard 2-key Triple-DES
     * construction in which the effective key strength is 112 bits.
     *
     * <p><b>Caveat:</b> This implementation only supports 16-byte (2-key 3DES)
     * expansion.  24-byte keys are not supported and will cause an
     * {@link IllegalArgumentException}.
     *
     * @param key 16-byte 2-key 3DES key
     * @return 24-byte 3-key 3DES key ({@code K1 ‖ K2 ‖ K1})
     * @throws IllegalArgumentException if {@code key.length != 16}
     */
    public static byte[] expand3DesKey(byte[] key) {
        if (key.length != 16) {
            throw new IllegalArgumentException("SCP02 currently requires 16-byte 2-key 3DES keys");
        }
        byte[] expanded = new byte[24];
        System.arraycopy(key, 0, expanded, 0, 16);
        System.arraycopy(key, 0, expanded, 16, 8);
        return expanded;
    }

    // ─── ISO 9797 Method 2 padding ──────────────────────────────────────

    /**
     * Applies ISO/IEC 9797-1 Padding Method 2 to {@code input}.
     *
     * <p>The padding scheme appends {@code 0x80} immediately after the last
     * data byte, then pads with {@code 0x00} bytes until the total length is
     * a multiple of {@code blockSize}.  Defined in GlobalPlatform Card
     * Specification §B.2.3:
     * <blockquote>
     * "Append an '80' to the right of the data block; if the resultant data
     *  block length is a multiple of 16, no further padding is required;
     *  append binary zeroes to the right of the data block until the data
     *  block length is a multiple of 16."
     * </blockquote>
     *
     * <p>Used before AES-CBC encryption (SCP03 C-DECRYPTION / R-ENCRYPTION,
     * Amd D §6.2.6) and before 3DES MAC operations (SCP02).
     *
     * @param input     data to be padded (may be empty)
     * @param blockSize cipher block size in bytes (8 for 3DES, 16 for AES)
     * @return new array containing {@code input} followed by Method 2 padding
     */
    public static byte[] padIso9797Method2(byte[] input, int blockSize) {
        int paddedLength = ((input.length / blockSize) + 1) * blockSize;
        byte[] padded = Arrays.copyOf(input, paddedLength);
        padded[input.length] = (byte) 0x80;
        return padded;
    }

    /**
     * Removes ISO/IEC 9797-1 Padding Method 2 from {@code padded}.
     *
     * <p>Scans from the end of the array, skipping {@code 0x00} bytes, and
     * expects to find the mandatory {@code 0x80} marker byte.  Returns a
     * copy of the array without the padding.
     *
     * @param padded padded data array
     * @return new array with padding stripped
     * @throws IllegalStateException if the padding is malformed (no {@code 0x80}
     *                                marker found before a non-zero byte)
     */
    public static byte[] removeIso9797Method2Padding(byte[] padded) {
        int index = padded.length - 1;
        while (index >= 0 && padded[index] == 0x00) {
            index -= 1;
        }
        if (index < 0 || padded[index] != (byte) 0x80) {
            throw new IllegalStateException("Invalid ISO 9797 Method 2 padding");
        }
        return Arrays.copyOf(padded, index);
    }

    // ─── Shared byte utilities (crypto-relevant) ─────────────────────────

    /**
     * XORs two equal-length byte arrays.
     *
     * <p>Used internally by {@link #cmac} to combine block-cipher state with
     * input blocks and subkeys.
     *
     * @param left  first operand
     * @param right second operand; must have the same length as {@code left}
     * @return new array containing {@code left[i] ^ right[i]} for each index
     * @throws IllegalArgumentException if the arrays differ in length
     */
    public static byte[] xor(byte[] left, byte[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("xor operands must have equal length");
        }
        byte[] result = new byte[left.length];
        for (int index = 0; index < left.length; index++) {
            result[index] = (byte) (left[index] ^ right[index]);
        }
        return result;
    }
}
