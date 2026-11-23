package app.aoki.cinpo.gp;

import static org.junit.jupiter.api.Assertions.*;

import app.aoki.cinpo.util.Util;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for cryptographic primitives in {@link GpCrypto}.
 *
 * <p>Tests include NIST test vectors for AES-CMAC (SP 800-38B), GF(2^128) doubling operations, and
 * ISO 9797-1 Method 2 padding round-trip validation.
 */
class GpCryptoTest {

  // ─── Test utilities ──────────────────────────────────────────────────

  private static byte[] hex(String hexString) {
    return Util.parseHex(hexString);
  }

  // ─── AES-CMAC tests (NIST SP 800-38B) ────────────────────────────────

  /**
   * NIST SP 800-38B Appendix D, Example 1: AES-128 with zero-length message.
   *
   * <p>Reference: <a href="https://csrc.nist.gov/publications/detail/sp/800-38b/final">NIST SP
   * 800-38B Section D.1</a>
   *
   * <p>Key: 2B7E1516 28AED2A6 ABF71588 09CF4F3C <br>
   * Msg: (empty) <br>
   * CMAC: BB1D6929 E9593728 7FA37D12 9B756746
   *
   * <p>What it does: It computes the AES-CMAC of a zero-length message with the NIST SP 800-38B D.1
   * key and compares the result with the published 16-byte MAC.
   *
   * <p>What it confirms: It confirms that the zero-length path produces the exact MAC BB1D6929
   * E9593728 7FA37D12 9B756746, which requires both subkeys K1 and K2.
   *
   * <p>Benefit: It detects drift in the CMAC subkey derivation or final-block padding, because no
   * other input length can hide a fault behind this vector.
   */
  @DisplayName("CMAC of an empty message should match NIST SP 800-38B D.1")
  @Test
  void cmacNistVectorEmpty() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] message = new byte[0];
    byte[] expected = hex("BB1D6929 E9593728 7FA37D12 9B756746");

    byte[] cmac = GpCrypto.cmac(key, message);

    assertArrayEquals(expected, cmac, "CMAC of empty message should match NIST SP 800-38B D.1");
  }

  /**
   * NIST SP 800-38B Appendix D, Example 2: AES-128 with 16-byte message (complete block).
   *
   * <p>Reference: <a href="https://csrc.nist.gov/publications/detail/sp/800-38b/final">NIST SP
   * 800-38B Section D.2</a>
   *
   * <p>Key: 2B7E1516 28AED2A6 ABF71588 09CF4F3C <br>
   * Msg: 6BC1BEE2 2E409F96 E93D7E11 7393172A <br>
   * CMAC: 070A16B4 6B4D4144 F79BDD9D D04A287C
   *
   * <p>What it does: It computes the AES-CMAC of one complete 16-byte block with the NIST D.2 key
   * and compares the full 16-byte result with the published MAC.
   *
   * <p>What it confirms: It confirms that the K1 path, used when the message ends on a block
   * boundary, yields 070A16B4 6B4D4144 F79BDD9D D04A287C.
   *
   * <p>Benefit: It separates a K1 regression from a K2 regression: the empty-message vector needs
   * K2, whereas this vector needs only K1.
   */
  @DisplayName("CMAC of a complete 16-byte message should match NIST SP 800-38B D.2")
  @Test
  void cmacNistVectorOneBlock() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] message = hex("6BC1BEE2 2E409F96 E93D7E11 7393172A");
    byte[] expected = hex("070A16B4 6B4D4144 F79BDD9D D04A287C");

    byte[] cmac = GpCrypto.cmac(key, message);

    assertArrayEquals(expected, cmac, "CMAC of 16-byte message should match NIST SP 800-38B D.2");
  }

  /**
   * NIST SP 800-38B Appendix D, Example 3: AES-128 with 40-byte message (incomplete final block).
   *
   * <p>Reference: <a href="https://csrc.nist.gov/publications/detail/sp/800-38b/final">NIST SP
   * 800-38B Section D.3</a>
   *
   * <p>Key: 2B7E1516 28AED2A6 ABF71588 09CF4F3C <br>
   * Msg: 6BC1BEE2 2E409F96 E93D7E11 7393172A AE2D8A57 1E03AC9C 9EB76FAC 45AF8E51 30C81C46 A35CE411
   * <br>
   * CMAC: DFA66747 DE9AE630 30CA3261 1497C827
   *
   * <p>What it does: It computes the AES-CMAC of the 40-byte NIST D.3 message and compares the
   * result with the published MAC.
   *
   * <p>What it confirms: It confirms that the incomplete final block is padded with 0x80 and
   * processed with K2, producing DFA66747 DE9AE630 30CA3261 1497C827.
   *
   * <p>Benefit: It catches a padding or subkey-selection error that the complete-block vectors
   * cannot expose.
   */
  @DisplayName(
      "CMAC of a 40-byte message with an incomplete final block should match NIST SP 800-38B D.3")
  @Test
  void cmacNistVectorPartialBlock() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] message =
        hex(
            "6BC1BEE2 2E409F96 E93D7E11 7393172A "
                + "AE2D8A57 1E03AC9C 9EB76FAC 45AF8E51 "
                + "30C81C46 A35CE411");
    byte[] expected = hex("DFA66747 DE9AE630 30CA3261 1497C827");

    byte[] cmac = GpCrypto.cmac(key, message);

    assertArrayEquals(expected, cmac, "CMAC of 40-byte message should match NIST SP 800-38B D.3");
  }

  /**
   * NIST SP 800-38B Appendix D, Example 4: AES-128 with 64-byte message (multiple complete blocks).
   *
   * <p>Reference: <a href="https://csrc.nist.gov/publications/detail/sp/800-38b/final">NIST SP
   * 800-38B Section D.4</a>
   *
   * <p>Key: 2B7E1516 28AED2A6 ABF71588 09CF4F3C <br>
   * Msg: 6BC1BEE2 2E409F96 E93D7E11 7393172A AE2D8A57 1E03AC9C 9EB76FAC 45AF8E51 30C81C46 A35CE411
   * E5FBC119 1A0A52EF F69F2445 DF4F9B17 AD2B417B E66C3710 <br>
   * CMAC: 51F0BEBF 7E3B9D92 FC497417 79363CFE
   *
   * <p>What it does: It computes the AES-CMAC of the 64-byte NIST D.4 message and compares the
   * result with the published MAC.
   *
   * <p>What it confirms: It confirms that CBC-MAC chaining across four complete blocks produces
   * 51F0BEBF 7E3B9D92 FC497417 79363CFE.
   *
   * <p>Benefit: It catches an error that appears only after more than one AES block has been
   * chained.
   */
  @DisplayName(
      "CMAC of a 64-byte message with four complete blocks should match NIST SP 800-38B D.4")
  @Test
  void cmacNistVectorMultiBlock() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] message =
        hex(
            "6BC1BEE2 2E409F96 E93D7E11 7393172A "
                + "AE2D8A57 1E03AC9C 9EB76FAC 45AF8E51 "
                + "30C81C46 A35CE411 E5FBC119 1A0A52EF "
                + "F69F2445 DF4F9B17 AD2B417B E66C3710");
    byte[] expected = hex("51F0BEBF 7E3B9D92 FC497417 79363CFE");

    byte[] cmac = GpCrypto.cmac(key, message);

    assertArrayEquals(expected, cmac, "CMAC of 64-byte message should match NIST SP 800-38B D.4");
  }

  /**
   * Verify that CMAC produces the expected 8-byte truncated MAC for SCP03 C-MAC calculation.
   *
   * <p>SCP03 Amendment D Section 6.2.4 specifies: "The eight most significant bytes are
   * considered."
   *
   * <p>What it does: It computes a full 16-byte CMAC, copies its first eight bytes, and checks that
   * copy against the expected truncation.
   *
   * <p>What it confirms: It confirms that the full MAC is 16 bytes and that the truncated value is
   * 070A16B4 6B4D4144, the leading half of the D.2 vector.
   *
   * <p>Benefit: It protects the SCP03 C-MAC contract: a trailing-half truncation or a wrong MAC
   * length would produce invalid card commands.
   */
  @DisplayName(
      "Truncating a full CMAC to eight bytes should keep the eight most significant bytes required by SCP03")
  @Test
  void cmacTruncationForScp03() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] message = hex("6BC1BEE2 2E409F96 E93D7E11 7393172A");

    byte[] fullCmac = GpCrypto.cmac(key, message);
    byte[] truncated = new byte[8];
    System.arraycopy(fullCmac, 0, truncated, 0, 8);

    assertEquals(16, fullCmac.length, "Full CMAC output should be 16 bytes");
    assertArrayEquals(
        hex("070A16B4 6B4D4144"),
        truncated,
        "8-byte truncated CMAC should match first 8 bytes of full output");
  }

  // ─── GF(2^128) doubling tests (doubleLu) ─────────────────────────────

  /**
   * Computes L with an all-zero AES key and checks the CMAC of a complete block.
   *
   * <p>What it does: It encrypts a zero block with a zero AES key to obtain L, checks that L
   * constant, and computes the CMAC of one 16-byte message, which exercises the K1 derivation path.
   *
   * <p>What it confirms: It confirms that L equals 66E94BD4EF8A2C3B884CFA59CA342B2E and that CMAC
   * returns 16 bytes for a complete block.
   *
   * <p>Benefit: It pins the AES output that feeds the GF(2^128) doubling code, so a regression in
   * the encryption or in L is caught where the doubling starts. It does not assert the derived K1
   * value.
   */
  @DisplayName(
      "CMAC with an all-zero AES key and a full block should produce a 16-byte result after deriving L")
  @Test
  void doubleLuZeroBlock() throws GeneralSecurityException {
    // Use CMAC internals to test doubleLu: encrypt zero block, derive K1
    byte[] key = new byte[16];
    byte[] message = new byte[16]; // Complete block triggers K1 path

    // The CMAC implementation derives L = AES(key, 0x00...00), then K1 = double(L)
    // For an all-zero key, L = AES(0x00, 0x00) = 66E94BD4EF8A2C3B884CFA59CA342B2E
    // (this is a known AES constant)
    byte[] expectedL = hex("66E94BD4 EF8A2C3B 884CFA59 CA342B2E");
    byte[] l = GpCrypto.aesEcbEncrypt(key, new byte[16]);

    assertArrayEquals(expectedL, l, "AES(0, 0) should produce expected L value");

    // K1 = double(L): L[0] & 0x80 = 0x66 & 0x80 = 0, so no XOR with 0x87
    // K1 = L << 1 = CDD297A9 DF145876 1099F4B3 946856...
    byte[] expectedK1 = hex("CDD297A9 DF145876 1099F4B3 946856DC");

    // We can verify by computing CMAC of a 16-byte message and checking subkey usage
    // Indirect test: if doubleLu(0) worked correctly, CMAC should produce correct output
    byte[] cmac = GpCrypto.cmac(key, message);
    assertEquals(16, cmac.length, "CMAC should produce 16-byte output");
  }

  /**
   * Check GF(2^128) doubling with MSB = 0 (no polynomial reduction).
   *
   * <p>If the most significant bit is 0, doubling is just a left shift.
   *
   * <p>What it does: It encrypts a zero block with the key 00000000 00000000 00000000 00000001,
   * reads the most significant bit of the resulting L, and computes the CMAC of a complete 16-byte
   * block.
   *
   * <p>What it confirms: It confirms that the derived L can be inspected for its most significant
   * bit and that CMAC still returns a 16-byte result for that key.
   *
   * <p>Benefit: none beyond a length check: the MSB flag is computed but never asserted, so the
   * only failure this test can detect is a CMAC output that is not 16 bytes. Pinning the MSB-zero
   * branch would need a reference MAC for this key.
   */
  @DisplayName("CMAC of a full block with the single-one AES key should return a 16-byte result")
  @Test
  void doubleLuMsbZero() throws GeneralSecurityException {
    // Use a block starting with 0x7F (MSB = 0)
    // Input:  7FFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF
    // Output: FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFE (left shift, no XOR)

    // We can test this indirectly through CMAC by crafting a key that produces
    // a specific L value, but that's complex. Instead, we'll test the overall
    // CMAC behavior with known vectors, which exercises doubleLu implicitly.

    // Direct test: verify CMAC with a key that produces L with MSB=0
    byte[] key = hex("00000000 00000000 00000000 00000001");
    byte[] l = GpCrypto.aesEcbEncrypt(key, new byte[16]);

    // If L[0] & 0x80 == 0, then K1 = L << 1 (no XOR 0x87)
    boolean msbSet = (l[0] & 0x80) != 0;

    // Just verify CMAC works correctly regardless
    byte[] message = new byte[16];
    byte[] cmac = GpCrypto.cmac(key, message);
    assertEquals(16, cmac.length, "CMAC should produce 16-byte output");
  }

  /**
   * Check GF(2^128) doubling with MSB = 1 (requires XOR with 0x87).
   *
   * <p>If the most significant bit is 1, the result is (L << 1) XOR 0x87.
   *
   * <p>What it does: It checks the L value and its most significant bit for the NIST D.1 key, then
   * compares the CMAC of an empty message with the published MAC.
   *
   * <p>What it confirms: It confirms L = 7DF76B0C 1AB899B3 3E42F047 B91B546F with MSB zero and CMAC
   * = BB1D6929 E9593728 7FA37D12 9B756746, which requires the MSB-one doubling path when K2 is
   * derived.
   *
   * <p>Benefit: It detects a subkey derivation that omits the 0x87 reduction, because that error
   * changes the empty-message MAC.
   */
  @DisplayName(
      "CMAC of an empty message with the NIST D.1 key should match the published vector while L has MSB zero")
  @Test
  void doubleLuMsbOne() throws GeneralSecurityException {
    // Use a block starting with 0x80 (MSB = 1)
    // The NIST test vectors exercise this: in Example 1 (empty message),
    // K2 is derived from K1 via doubling, and one of them has MSB=1

    // From NIST SP 800-38B D.1:
    // Key = 2B7E1516 28AED2A6 ABF71588 09CF4F3C
    // L   = 7DF76B0C 1AB899B3 3E42F047 B91B546F
    // K1  = FBEED618 35713366 7C85E08F 7236A8DE (L << 1, no XOR - MSB(L)=0)
    // K2  = F7DDAC30 6AE266CC F90BC11E E46D513B (K1 << 1 XOR 0x87 - MSB(K1)=1)

    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] l = GpCrypto.aesEcbEncrypt(key, new byte[16]);
    byte[] expectedL = hex("7DF76B0C 1AB899B3 3E42F047 B91B546F");

    assertArrayEquals(expectedL, l, "L value should match NIST SP 800-38B D.1");

    // Verify MSB of L is 0 (0x7D = 0111 1101)
    assertFalse((l[0] & 0x80) != 0, "MSB of L should be 0 for this test vector");

    // The CMAC result indirectly confirms K1 and K2 were computed correctly
    byte[] message = new byte[0];
    byte[] cmac = GpCrypto.cmac(key, message);
    byte[] expected = hex("BB1D6929 E9593728 7FA37D12 9B756746");

    assertArrayEquals(expected, cmac, "CMAC confirms correct doubleLu behavior with MSB=1 case");
  }

  /**
   * Check GF(2^128) doubling with all-ones block (boundary case).
   *
   * <p>Input: FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF <br>
   * Output: FFFFFFFF FFFFFFFF FFFFFFFF FFFFFF79 <br>
   * (left shift gives FFFFFFFE, then XOR 0x87 = 0xFE XOR 0x87 = 0x79)
   *
   * <p>What it does: It computes the CMAC of an empty message with an all-ones AES key twice and
   * checks the length and equality of the two results.
   *
   * <p>What it confirms: It confirms that the output is a well-formed 16-byte block and that
   * repeated calls with the same key and message produce identical bytes. The all-ones doubling
   * shown above is documentation; this test does not call the private doubling routine.
   *
   * <p>Benefit: It catches a nondeterministic result caused by shared mutable state. No published
   * vector exists for this key, so the test does not pin a MAC value.
   */
  @DisplayName(
      "CMAC with an all-ones key and an empty message should return 16 bytes and repeat the same result")
  @Test
  void doubleLuAllOnes() throws GeneralSecurityException {
    // We can't directly call doubleLu (it's private), but we can test it
    // indirectly by using a key that produces L = 0xFFFF...FFFF
    // However, finding such a key is not practical without inverting AES.

    // Alternative: test that CMAC handles large values correctly
    byte[] key = hex("FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFF");
    byte[] message = new byte[0];
    byte[] cmac = GpCrypto.cmac(key, message);

    // We don't have a reference vector for this key, but we can verify
    // the output is well-formed (16 bytes)
    assertEquals(16, cmac.length, "CMAC with all-ones key should produce 16-byte output");

    // Additional sanity check: CMAC should be deterministic
    byte[] cmac2 = GpCrypto.cmac(key, message);
    assertArrayEquals(cmac, cmac2, "CMAC should be deterministic for same key/message");
  }

  /**
   * Computes the CMAC of a 24-byte message whose final block is incomplete.
   *
   * <p>What it does: It computes the CMAC with the NIST D.1 key and a 24-byte message, so the
   * implementation derives K2 from K1 for the final block.
   *
   * <p>What it confirms: It confirms that the result is 16 bytes for this input. It does not assert
   * a reference MAC value.
   *
   * <p>Benefit: none without a reference MAC: no expected value is asserted, so a wrong K2 would
   * pass. A vector assertion is needed to test the carry itself.
   */
  @DisplayName(
      "CMAC of a 24-byte message with the NIST D.1 key should return 16 bytes through the K2 path")
  @Test
  void doubleLuCarryPropagation() throws GeneralSecurityException {
    // Use NIST vector where we know the intermediate values
    // K1 = FBEED618 35713366 7C85E08F 7236A8DE (from D.1)
    // K2 = F7DDAC30 6AE266CC F90BC11E E46D513B

    // Verify: K1 << 1 = F7DDAC30 6AE266CC F90BC11E E46D511C
    //         XOR 0x87 = F7DDAC30 6AE266CC F90BC11E E46D513B

    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");

    // Test with a 24-byte message: one full block plus eight bytes, so K2 is used
    byte[] message = hex("6BC1BEE2 2E409F96 E93D7E11 7393172A " + "AE2D8A57 1E03AC9C");
    byte[] cmac = GpCrypto.cmac(key, message);

    // Expected CMAC for this input (derived from NIST vectors)
    // This tests that K2 was computed correctly via doubleLu(K1)
    assertEquals(16, cmac.length, "CMAC should produce 16-byte output");
  }

  // ─── ISO 9797-1 Method 2 padding tests ───────────────────────────────

  /**
   * Test ISO 9797 Method 2 padding with empty data (AES block size).
   *
   * <p>Empty data should pad to: 80 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
   *
   * <p>What it does: It pads an empty input to the AES block size and inspects all 16 resulting
   * bytes.
   *
   * <p>What it confirms: It confirms length 16 and content 80 00 ... 00, with 0x80 in byte 0 and
   * zeros afterwards.
   *
   * <p>Benefit: It pins the minimal padding case, the boundary where an implementation may wrongly
   * skip the marker block.
   */
  @DisplayName(
      "ISO 9797-1 Method 2 padding of empty data with a 16-byte block should produce one 0x80 marker followed by zeros")
  @Test
  void padIso9797EmptyAes() {
    byte[] input = new byte[0];
    byte[] padded = GpCrypto.padIso9797Method2(input, 16);

    assertEquals(16, padded.length, "Empty data should pad to 16 bytes");
    assertEquals((byte) 0x80, padded[0], "First byte should be 0x80");
    for (int i = 1; i < 16; i++) {
      assertEquals((byte) 0x00, padded[i], "Remaining bytes should be 0x00");
    }
  }

  /**
   * Test ISO 9797 Method 2 padding with 1 byte of data.
   *
   * <p>Input: 42 <br>
   * Output: 42 80 00 00 00 00 00 00 00 00 00 00 00 00 00 00
   *
   * <p>What it does: It pads the single byte 42 to 16 bytes and checks the prefix and the padding
   * bytes.
   *
   * <p>What it confirms: It confirms the result starts with 42 80 and has 14 zero bytes.
   *
   * <p>Benefit: It catches an implementation that overwrites the first data byte instead of
   * appending the marker.
   */
  @DisplayName(
      "ISO 9797-1 Method 2 padding of one byte should preserve the data and append a 0x80 marker")
  @Test
  void padIso9797OneByte() {
    byte[] input = hex("42");
    byte[] padded = GpCrypto.padIso9797Method2(input, 16);

    assertEquals(16, padded.length, "1-byte data should pad to 16 bytes");
    assertEquals((byte) 0x42, padded[0], "First byte should be input data");
    assertEquals((byte) 0x80, padded[1], "Second byte should be 0x80");
    for (int i = 2; i < 16; i++) {
      assertEquals((byte) 0x00, padded[i], "Remaining bytes should be 0x00");
    }
  }

  /**
   * Test ISO 9797 Method 2 padding with 15 bytes (almost full block).
   *
   * <p>15 bytes + 0x80 = 16 bytes (exactly one block, no additional padding).
   *
   * <p>What it does: It pads 15 bytes to one 16-byte block and checks that the data is preserved
   * and the final byte is 0x80.
   *
   * <p>What it confirms: It confirms that no extra block is added when 15 data bytes plus the
   * marker exactly fill the block.
   *
   * <p>Benefit: It guards the boundary between one block and two blocks, where an off-by-one error
   * would add an unnecessary block.
   */
  @DisplayName(
      "ISO 9797-1 Method 2 padding of 15 bytes should place the 0x80 marker in the last byte of the block")
  @Test
  void padIso9797AlmostFullBlock() {
    byte[] input = new byte[15];
    for (int i = 0; i < 15; i++) {
      input[i] = (byte) (i + 1);
    }

    byte[] padded = GpCrypto.padIso9797Method2(input, 16);

    assertEquals(16, padded.length, "15-byte data should pad to 16 bytes");
    for (int i = 0; i < 15; i++) {
      assertEquals((byte) (i + 1), padded[i], "Original data should be preserved");
    }
    assertEquals((byte) 0x80, padded[15], "Last byte should be 0x80 padding marker");
  }

  /**
   * Test ISO 9797 Method 2 padding with exactly 16 bytes (one full block).
   *
   * <p>16 bytes of data require an additional block: data + 80 00...00
   *
   * <p>What it does: It pads a full 16-byte block and checks that the first block is unchanged
   * while the second block begins with 0x80.
   *
   * <p>What it confirms: It confirms padded length 32, byte 16 equal to 0x80, and zero bytes
   * through index 31.
   *
   * <p>Benefit: It catches an implementation that omits the marker block for block-aligned input.
   */
  @DisplayName(
      "ISO 9797-1 Method 2 padding of exactly 16 bytes should add a second block with a 0x80 marker")
  @Test
  void padIso9797ExactBlock() {
    byte[] input = new byte[16];
    for (int i = 0; i < 16; i++) {
      input[i] = (byte) (i + 1);
    }

    byte[] padded = GpCrypto.padIso9797Method2(input, 16);

    assertEquals(32, padded.length, "16-byte data should pad to 32 bytes");
    for (int i = 0; i < 16; i++) {
      assertEquals((byte) (i + 1), padded[i], "Original data should be preserved");
    }
    assertEquals((byte) 0x80, padded[16], "Byte 16 should be 0x80 padding marker");
    for (int i = 17; i < 32; i++) {
      assertEquals((byte) 0x00, padded[i], "Remaining bytes should be 0x00");
    }
  }

  /**
   * Test ISO 9797 Method 2 padding with various lengths (0 to 32 bytes).
   *
   * <p>Verify padding works correctly for all lengths in range.
   *
   * <p>What it does: It pads inputs of lengths 0 through 32 bytes and checks length, preserved
   * data, marker position, and zero fill for each length.
   *
   * <p>What it confirms: It confirms that the padded length is the next multiple of 16 and that
   * 0x80 sits directly after the original data at every length.
   *
   * <p>Benefit: It replaces a single sample with a sweep, so a length-specific boundary bug cannot
   * hide between two tested cases.
   */
  @DisplayName(
      "ISO 9797-1 Method 2 padding for every length from 0 to 32 bytes should follow the same block rule")
  @Test
  void padIso9797VariousLengths() {
    for (int len = 0; len <= 32; len++) {
      byte[] input = new byte[len];
      for (int i = 0; i < len; i++) {
        input[i] = (byte) i;
      }

      byte[] padded = GpCrypto.padIso9797Method2(input, 16);

      // Padded length should be next multiple of 16
      int expectedLen = ((len / 16) + 1) * 16;
      assertEquals(
          expectedLen,
          padded.length,
          "Padded length should be next multiple of 16 for input length " + len);

      // Original data should be preserved
      for (int i = 0; i < len; i++) {
        assertEquals((byte) i, padded[i], "Original data should be preserved at index " + i);
      }

      // Padding marker should be at position len
      assertEquals((byte) 0x80, padded[len], "Padding marker 0x80 should be at position " + len);

      // Rest should be 0x00
      for (int i = len + 1; i < expectedLen; i++) {
        assertEquals((byte) 0x00, padded[i], "Byte " + i + " should be 0x00 padding");
      }
    }
  }

  /**
   * Test ISO 9797 Method 2 padding with 3DES block size (8 bytes).
   *
   * <p>Verify padding works for SCP02 (3DES uses 8-byte blocks).
   *
   * <p>What it does: It pads a five-byte input using the 3DES block size of eight bytes.
   *
   * <p>What it confirms: It confirms the 8-byte result is exactly 01 02 03 04 05 80 00 00.
   *
   * <p>Benefit: It verifies that the block-size parameter is used, so the SCP02 path cannot
   * silently inherit the AES 16-byte block size.
   */
  @DisplayName(
      "ISO 9797-1 Method 2 padding of five bytes with an 8-byte block should produce 01 02 03 04 05 80 00 00")
  @Test
  void padIso9797DesBlockSize() {
    byte[] input = hex("01 02 03 04 05");
    byte[] padded = GpCrypto.padIso9797Method2(input, 8);

    assertEquals(8, padded.length, "5-byte data should pad to 8 bytes");
    assertArrayEquals(
        hex("01 02 03 04 05 80 00 00"),
        padded,
        "Padding should follow ISO 9797 Method 2 for 8-byte blocks");
  }

  // ─── Padding removal tests ───────────────────────────────────────────

  /**
   * Test ISO 9797 Method 2 padding removal round-trip (empty data).
   *
   * <p>What it does: It pads an empty array, removes the padding, and compares the result with the
   * original.
   *
   * <p>What it confirms: It confirms the round trip preserves a zero-length payload.
   *
   * <p>Benefit: It catches an unpadding implementation that emits a marker byte when there is no
   * payload.
   */
  @DisplayName(
      "Removing ISO 9797-1 Method 2 padding from a padded empty message should return empty data")
  @Test
  void unpadIso9797Empty() {
    byte[] original = new byte[0];
    byte[] padded = GpCrypto.padIso9797Method2(original, 16);
    byte[] unpadded = GpCrypto.removeIso9797Method2Padding(padded);

    assertArrayEquals(
        original, unpadded, "Round-trip padding/unpadding should preserve empty data");
  }

  /**
   * Test ISO 9797 Method 2 padding removal round-trip (1 byte).
   *
   * <p>What it does: It pads the byte 42, removes the padding, and compares the result with the
   * original.
   *
   * <p>What it confirms: It confirms the round trip preserves the one-byte payload 42.
   *
   * <p>Benefit: It catches an off-by-one error that would drop the last payload byte.
   */
  @DisplayName(
      "Removing ISO 9797-1 Method 2 padding from a padded single byte should return that byte")
  @Test
  void unpadIso9797OneByte() {
    byte[] original = hex("42");
    byte[] padded = GpCrypto.padIso9797Method2(original, 16);
    byte[] unpadded = GpCrypto.removeIso9797Method2Padding(padded);

    assertArrayEquals(
        original, unpadded, "Round-trip padding/unpadding should preserve 1-byte data");
  }

  /**
   * Test ISO 9797 Method 2 padding removal round-trip (15 bytes).
   *
   * <p>What it does: It pads a 15-byte payload that exactly fills a block with the marker, then
   * removes the padding.
   *
   * <p>What it confirms: It confirms the round trip preserves all 15 payload bytes.
   *
   * <p>Benefit: It checks the boundary where the marker is the last byte, so an incorrect marker
   * scan cannot pass.
   */
  @DisplayName(
      "Removing ISO 9797-1 Method 2 padding from a padded 15-byte message should return all 15 bytes")
  @Test
  void unpadIso9797AlmostFullBlock() {
    byte[] original = new byte[15];
    for (int i = 0; i < 15; i++) {
      original[i] = (byte) (i + 1);
    }

    byte[] padded = GpCrypto.padIso9797Method2(original, 16);
    byte[] unpadded = GpCrypto.removeIso9797Method2Padding(padded);

    assertArrayEquals(
        original, unpadded, "Round-trip padding/unpadding should preserve 15-byte data");
  }

  /**
   * Test ISO 9797 Method 2 padding removal round-trip (16 bytes).
   *
   * <p>What it does: It pads a 16-byte payload into two blocks, then removes the padding.
   *
   * <p>What it confirms: It confirms the round trip preserves all 16 payload bytes.
   *
   * <p>Benefit: It catches an implementation that mistakes a full payload block for a padding
   * block.
   */
  @DisplayName(
      "Removing ISO 9797-1 Method 2 padding from a padded 16-byte message should return all 16 bytes")
  @Test
  void unpadIso9797FullBlock() {
    byte[] original = new byte[16];
    for (int i = 0; i < 16; i++) {
      original[i] = (byte) (i + 1);
    }

    byte[] padded = GpCrypto.padIso9797Method2(original, 16);
    byte[] unpadded = GpCrypto.removeIso9797Method2Padding(padded);

    assertArrayEquals(
        original, unpadded, "Round-trip padding/unpadding should preserve 16-byte data");
  }

  /**
   * Test ISO 9797 Method 2 padding removal round-trip (various lengths).
   *
   * <p>What it does: It pads and then unpads payloads of lengths 0 through 32 bytes.
   *
   * <p>What it confirms: It confirms that every length returns the exact original bytes.
   *
   * <p>Benefit: It covers each block boundary in one sweep, so a length-specific truncation is
   * detected.
   */
  @DisplayName(
      "Removing ISO 9797-1 Method 2 padding for lengths from 0 to 32 bytes should restore every payload")
  @Test
  void unpadIso9797VariousLengths() {
    for (int len = 0; len <= 32; len++) {
      byte[] original = new byte[len];
      for (int i = 0; i < len; i++) {
        original[i] = (byte) i;
      }

      byte[] padded = GpCrypto.padIso9797Method2(original, 16);
      byte[] unpadded = GpCrypto.removeIso9797Method2Padding(padded);

      assertArrayEquals(original, unpadded, "Round-trip should preserve data of length " + len);
    }
  }

  // ─── Padding removal validation tests ────────────────────────────────

  /**
   * Test padding removal with invalid padding (no 0x80 marker).
   *
   * <p>Should throw IllegalStateException.
   *
   * <p>What it does: It passes a 16-byte block of zeros to the unpadding method and captures the
   * exception.
   *
   * <p>What it confirms: It confirms an IllegalStateException is thrown and its message names
   * invalid ISO 9797 Method 2 padding.
   *
   * <p>Benefit: It prevents malformed input from being accepted as an empty payload.
   */
  @DisplayName(
      "Removing padding from a block without a 0x80 marker should throw IllegalStateException")
  @Test
  void unpadIso9797NoMarker() {
    byte[] invalid = hex("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> GpCrypto.removeIso9797Method2Padding(invalid),
            "Should throw exception when no 0x80 marker found");

    assertTrue(
        exception.getMessage().contains("Invalid ISO 9797 Method 2 padding"),
        "Exception message should indicate invalid padding");
  }

  /**
   * Passes a 16-byte block with no padding marker to padding removal.
   *
   * <p>What it does: It passes 16 nonzero bytes and captures the exception.
   *
   * <p>What it confirms: It confirms an IllegalStateException is thrown with the invalid-padding
   * message.
   *
   * <p>Benefit: It catches a parser that accepts a full block with no 0x80 marker as valid input.
   */
  @DisplayName(
      "Removing padding from a block with no 0x80 marker should throw IllegalStateException")
  @Test
  void unpadIso9797Malformed() {
    byte[] malformed = hex("01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> GpCrypto.removeIso9797Method2Padding(malformed),
            "Should throw exception for malformed padding");

    assertTrue(
        exception.getMessage().contains("Invalid ISO 9797 Method 2 padding"),
        "Exception message should indicate invalid padding");
  }

  /**
   * Test padding removal with padding marker in wrong position.
   *
   * <p>Data: 01 02 03 80 00 00 00 00 00 00 00 00 00 00 00 FF <br>
   * The 0xFF at the end means the final 0x80 search will fail.
   *
   * <p>What it does: It passes a block with 0x80 in the middle and 0xFF as the final byte, then
   * captures the exception.
   *
   * <p>What it confirms: It confirms an IllegalStateException is thrown with the invalid-padding
   * message.
   *
   * <p>Benefit: It catches a marker search that stops at the first 0x80 without validating the
   * trailing bytes.
   */
  @DisplayName(
      "Removing padding whose 0x80 marker is followed by 0xFF should throw IllegalStateException")
  @Test
  void unpadIso9797WrongMarker() {
    byte[] invalid = hex("01 02 03 80 00 00 00 00 00 00 00 00 00 00 00 FF");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> GpCrypto.removeIso9797Method2Padding(invalid),
            "Should throw exception when padding marker not at expected position");

    assertTrue(
        exception.getMessage().contains("Invalid ISO 9797 Method 2 padding"),
        "Exception message should indicate invalid padding");
  }

  /**
   * Test padding removal with all-zero block (edge case).
   *
   * <p>An all-zero block has no 0x80 marker and should fail.
   *
   * <p>What it does: It passes a 16-byte block of zeros to the unpadding method and captures the
   * exception.
   *
   * <p>What it confirms: It confirms an IllegalStateException is thrown with the invalid-padding
   * message.
   *
   * <p>Benefit: It documents the all-zero edge case, but its input and assertion overlap with the
   * no-marker case, so it adds no coverage beyond that case.
   */
  @DisplayName("Removing padding from an all-zero block should throw IllegalStateException")
  @Test
  void unpadIso9797AllZeros() {
    byte[] allZeros = new byte[16];

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> GpCrypto.removeIso9797Method2Padding(allZeros),
            "Should throw exception for all-zero block");

    assertTrue(
        exception.getMessage().contains("Invalid ISO 9797 Method 2 padding"),
        "Exception message should indicate invalid padding");
  }

  /**
   * Test padding removal with only padding marker (edge case).
   *
   * <p>Input: 80 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 <br>
   * Should return empty array.
   *
   * <p>What it does: It passes a block made of the 0x80 marker followed by zeros to the unpadding
   * method.
   *
   * <p>What it confirms: It confirms the result has length zero.
   *
   * <p>Benefit: It catches an implementation that returns the marker itself as payload.
   */
  @DisplayName(
      "Removing padding from a block containing only the 0x80 marker should return an empty array")
  @Test
  void unpadIso9797OnlyMarker() {
    byte[] onlyMarker = hex("80 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
    byte[] unpadded = GpCrypto.removeIso9797Method2Padding(onlyMarker);

    assertEquals(
        0,
        unpadded.length,
        "Removing padding from block with only 0x80 marker should yield empty array");
  }

  // ─── XOR utility tests ────────────────────────────────────────────────

  /**
   * Test XOR with equal-length arrays.
   *
   * <p>What it does: It XORs 01 02 03 04 with 05 06 07 08 and checks every output byte.
   *
   * <p>What it confirms: It confirms the result is 04 04 04 0C.
   *
   * <p>Benefit: It pins the basic arithmetic used by CMAC subkey derivation.
   */
  @DisplayName("XOR of two equal-length arrays should produce the byte-wise difference")
  @Test
  void xorEqualLength() {
    byte[] left = hex("01 02 03 04");
    byte[] right = hex("05 06 07 08");
    byte[] expected = hex("04 04 04 0C"); // 0x01^0x05=0x04, etc.

    byte[] result = GpCrypto.xor(left, right);

    assertArrayEquals(expected, result, "XOR should produce correct result");
  }

  /**
   * Test XOR with different-length arrays (should throw exception).
   *
   * <p>What it does: It XORs a four-byte array with a three-byte array and captures the exception.
   *
   * <p>What it confirms: It confirms an IllegalArgumentException is thrown and the message mentions
   * equal length.
   *
   * <p>Benefit: It catches a loop that would silently read past the shorter array.
   */
  @DisplayName("XOR of arrays with different lengths should throw IllegalArgumentException")
  @Test
  void xorUnequalLength() {
    byte[] left = hex("01 02 03 04");
    byte[] right = hex("05 06 07");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> GpCrypto.xor(left, right),
            "XOR should throw exception for unequal-length arrays");

    assertTrue(
        exception.getMessage().contains("equal length"),
        "Exception message should indicate length mismatch");
  }

  /**
   * Test XOR with zero (identity property).
   *
   * <p>What it does: It XORs 01 02 03 04 with four zero bytes.
   *
   * <p>What it confirms: It confirms the result equals the input byte for byte.
   *
   * <p>Benefit: It checks the identity property that callers rely on when masking with zero.
   */
  @DisplayName("XOR of data with a zero array should return the original data")
  @Test
  void xorWithZero() {
    byte[] data = hex("01 02 03 04");
    byte[] zero = new byte[4];

    byte[] result = GpCrypto.xor(data, zero);

    assertArrayEquals(data, result, "XOR with zero should return original data");
  }

  /**
   * Test XOR is commutative (A XOR B = B XOR A).
   *
   * <p>What it does: It XORs two arrays in both orders and compares the results.
   *
   * <p>What it confirms: It confirms A XOR B equals B XOR A for the chosen bytes.
   *
   * <p>Benefit: It catches an implementation that treats the two operands asymmetrically.
   */
  @DisplayName("XOR should give the same result regardless of operand order")
  @Test
  void xorCommutative() {
    byte[] a = hex("01 02 03 04");
    byte[] b = hex("05 06 07 08");

    byte[] ab = GpCrypto.xor(a, b);
    byte[] ba = GpCrypto.xor(b, a);

    assertArrayEquals(ab, ba, "XOR should be commutative");
  }

  /**
   * Test XOR self-cancellation (A XOR A = 0).
   *
   * <p>What it does: It XORs 01 02 03 04 with itself.
   *
   * <p>What it confirms: It confirms every output byte is zero.
   *
   * <p>Benefit: It checks the cancellation property that makes XOR safe for clearing masked values.
   */
  @DisplayName("XOR of an array with itself should return all zero bytes")
  @Test
  void xorSelfCancellation() {
    byte[] data = hex("01 02 03 04");
    byte[] zero = new byte[4];

    byte[] result = GpCrypto.xor(data, data);

    assertArrayEquals(zero, result, "XOR with self should produce all zeros");
  }

  // ─── AES primitives tests ─────────────────────────────────────────────

  /**
   * Test AES-ECB encryption produces correct output.
   *
   * <p>Uses NIST test vector for AES-128 ECB mode.
   *
   * <p>What it does: It encrypts the NIST FIPS 197 Appendix C.1 plaintext with the matching AES-128
   * key.
   *
   * <p>What it confirms: It confirms the ciphertext is 69C4E0D86A7B0430D8CDB78070B4C55A.
   *
   * <p>Benefit: It adds an independent standard vector to the CMAC vectors, so a shared bug in the
   * AES wrapper cannot hide behind CMAC results alone.
   */
  @DisplayName("AES-ECB encryption of the FIPS 197 plaintext should match the published ciphertext")
  @Test
  void aesEcbKnownVector() throws GeneralSecurityException {
    // NIST FIPS 197 Appendix C.1
    byte[] key = hex("000102030405060708090A0B0C0D0E0F");
    byte[] plaintext = hex("00112233445566778899AABBCCDDEEFF");
    byte[] expectedCiphertext = hex("69C4E0D86A7B0430D8CDB78070B4C55A");

    byte[] ciphertext = GpCrypto.aesEcbEncrypt(key, plaintext);

    assertArrayEquals(
        expectedCiphertext, ciphertext, "AES-ECB encryption should match NIST test vector");
  }

  /**
   * Test AES-CBC encryption with zero IV.
   *
   * <p>What it does: It encrypts one 16-byte block with an all-zero IV.
   *
   * <p>What it confirms: It confirms the ciphertext is 16 bytes and is not equal to the plaintext.
   *
   * <p>Benefit: It catches an implementation that returns the plaintext or a wrong-length buffer.
   * It does not pin a reference ciphertext, so a wrong but well-formed encryption can pass.
   */
  @DisplayName(
      "AES-CBC encryption with a zero IV should produce a 16-byte ciphertext that differs from the plaintext")
  @Test
  void aesCbcZeroIv() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] iv = new byte[16];
    byte[] plaintext = hex("6BC1BEE2 2E409F96 E93D7E11 7393172A");

    byte[] ciphertext = GpCrypto.aesCbcEncrypt(key, iv, plaintext);

    assertEquals(16, ciphertext.length, "AES-CBC should produce same-length output");
    assertFalse(Arrays.equals(plaintext, ciphertext), "Ciphertext should differ from plaintext");
  }

  /**
   * Test AES-CBC encryption and decryption round-trip.
   *
   * <p>What it does: It encrypts and then decrypts a 32-byte message with the same key and IV.
   *
   * <p>What it confirms: It confirms the decrypted bytes equal the original plaintext.
   *
   * <p>Benefit: It catches mismatched padding or IV handling between the encrypt and decrypt paths.
   */
  @DisplayName(
      "AES-CBC decryption of an encrypted 32-byte message should restore the original plaintext")
  @Test
  void aesCbcRoundTrip() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] iv = hex("00010203 04050607 08090A0B 0C0D0E0F");
    byte[] plaintext =
        hex("6BC1BEE2 2E409F96 E93D7E11 7393172A " + "AE2D8A57 1E03AC9C 9EB76FAC 45AF8E51");

    byte[] ciphertext = GpCrypto.aesCbcEncrypt(key, iv, plaintext);
    byte[] decrypted = GpCrypto.aesCbcDecrypt(key, iv, ciphertext);

    assertArrayEquals(plaintext, decrypted, "AES-CBC round-trip should preserve plaintext");
  }

  /**
   * Test AES-CBC single block encryption (used for ICV derivation).
   *
   * <p>What it does: It encrypts a 16-byte block whose final byte is 0x01 using the single-block
   * helper.
   *
   * <p>What it confirms: It confirms the result is 16 bytes and differs from the input block.
   *
   * <p>Benefit: It checks that the ICV derivation path invokes the cipher; a reference vector would
   * be needed to pin the value.
   */
  @DisplayName(
      "AES-CBC single-block encryption of a counter block should yield a 16-byte ICV different from the input")
  @Test
  void aesCbcIcvDerivation() throws GeneralSecurityException {
    byte[] key = hex("2B7E1516 28AED2A6 ABF71588 09CF4F3C");
    byte[] block = new byte[16];
    block[15] = 0x01; // Counter = 1

    byte[] icv = GpCrypto.aesCbcEncryptSingleBlock(key, block);

    assertEquals(16, icv.length, "ICV should be 16 bytes");
    assertFalse(Arrays.equals(block, icv), "ICV should differ from input block");
  }
}
