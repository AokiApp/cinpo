package app.aoki.cinpo.gp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.aoki.cinpo.util.Util;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SCP02 known-answer tests for the public GpCrypto API surface.
 *
 * <p>Vectors: scll-backend-rustcrypto tests/scp02_kat.rs (docs.rs 1.0.5). GPCS v2.3.1 Appendix E
 * (i=0x55):
 *
 * <ul>
 *   <li>S-ENC / S-MAC / S-RMAC / S-DEK derivation constants 0x0182 / 0x0101 / 0x0102 / 0x0181
 *   <li>HOST challenge 0001020304050607, CARD challenge 08090A0B0C0D, SEQ 0001
 *   <li>Base 3DES key 404142434445464748494A4B4C4D4E4F (as ENC/MAC/DEK)
 * </ul>
 *
 * <p>Independently cross-checked against a pyca/cryptography reference implementation of the same
 * scheme (matches all five derived values), so these values are not the output of any single
 * implementation.
 */
class Scp02CryptoFunctionsTest {

  /**
   * Expands a 16-byte base key with expand3DesKey and checks the resulting 24-byte EDE2 key.
   *
   * <p>This test checks that the expansion repeats the first 8 key bytes as the third DES key.
   *
   * <p>Benefit: a wrong expansion silently breaks every SCP02 session key and cryptogram, so this
   * test pins the primitive before the higher-level vectors depend on it.
   */
  @Test
  @DisplayName("expand3DesKey should expand a 16-byte key into its 24-byte EDE2 form")
  void expandsKeyToEde2() throws Exception {
    byte[] expanded = GpCrypto.expand3DesKey(Util.parseHex("404142434445464748494A4B4C4D4E4F"));
    assertEquals("404142434445464748494A4B4C4D4E4F4041424344454647", Util.toHex(expanded));
  }

  /**
   * Derives the four SCP02 session keys from the base key and the GPCS Appendix E derivation
   * constants.
   *
   * <p>This test checks S-ENC (0x0182), S-MAC (0x0101), S-RMAC (0x0102), and S-DEK (0x0181) against
   * the known-answer values from the reference vectors.
   *
   * <p>Benefit: these keys feed every later cryptogram and MAC, so this test confines a wrong
   * derivation constant to key derivation instead of a confusing failure at authentication.
   */
  @Test
  @DisplayName("Deriving S-ENC, S-MAC, S-RMAC, and S-DEK should match the reference values")
  void derivesSessionKeys() throws Exception {
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] iv = new byte[8];

    // S-ENC: derivation constant 0x0182 (GPCS §E.4.1)
    byte[] sEnc =
        GpCrypto.des3CbcEncrypt(
            GpCrypto.expand3DesKey(key),
            iv,
            Util.concat(new byte[] {0x01, (byte) 0x82, 0x00, 0x01}, new byte[12]));
    assertEquals("25C9794A1205FF244F5FA0378D2F8D59", Util.toHex(sEnc));

    // S-MAC: derivation constant 0x0101
    byte[] sMac =
        GpCrypto.des3CbcEncrypt(
            GpCrypto.expand3DesKey(key),
            iv,
            Util.concat(new byte[] {0x01, 0x01, 0x00, 0x01}, new byte[12]));
    assertEquals("9BED98891580C3B245FE9EC58BFA8D2A", Util.toHex(sMac));

    // S-RMAC: derivation constant 0x0102
    byte[] sRmac =
        GpCrypto.des3CbcEncrypt(
            GpCrypto.expand3DesKey(key),
            iv,
            Util.concat(new byte[] {0x01, 0x02, 0x00, 0x01}, new byte[12]));
    assertEquals("8EB6CF25BA4DECD820BF4E9FA616C50A", Util.toHex(sRmac));

    // S-DEK: derivation constant 0x0181
    byte[] sDek =
        GpCrypto.des3CbcEncrypt(
            GpCrypto.expand3DesKey(key),
            iv,
            Util.concat(new byte[] {0x01, (byte) 0x81, 0x00, 0x01}, new byte[12]));
    assertEquals("0E51FDF196141F227A57BD154012FD39", Util.toHex(sDek));
  }

  /**
   * Computes the card and host cryptograms used by SCP02 mutual authentication from the GPCS
   * vectors.
   *
   * <p>This test checks the ISO 9797-1 Method 2 padded inputs, the operand order on each side, and
   * the resulting 8-byte MAC values.
   *
   * <p>Benefit: the two cryptograms use the same data in opposite order, so this test catches a
   * swapped order that a self-consistent but non-interoperable implementation would hide.
   */
  @Test
  @DisplayName(
      "Card and host cryptograms computed from the GPCS vectors should match the known-answer values")
  void computesMutualAuthenticationCryptograms() throws Exception {
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] sEnc =
        GpCrypto.des3CbcEncrypt(
            GpCrypto.expand3DesKey(key),
            new byte[8],
            Util.concat(new byte[] {0x01, (byte) 0x82, 0x00, 0x01}, new byte[12]));

    // card cryptogram: full 3DES-CBC-MAC over ISO 9797-1 Method 2 padded
    // host challenge(8) || sequence counter(2) || card challenge(6)
    byte[] padded =
        GpCrypto.padIso9797Method2(
            Util.concat(
                Util.parseHex("0001020304050607"),
                Util.concat(new byte[] {0x00, 0x01}, Util.parseHex("08090A0B0C0D"))),
            8);
    byte[] cardCrypto = GpCrypto.full3DesCbcMac(sEnc, padded);
    assertEquals(
        "9D8DA672F05C4E45",
        Util.toHex(Arrays.copyOfRange(cardCrypto, cardCrypto.length - 8, cardCrypto.length)));

    // host cryptogram: card challenge || host challenge (the reverse order)
    byte[] paddedH =
        GpCrypto.padIso9797Method2(
            Util.concat(
                Util.concat(new byte[] {0x00, 0x01}, Util.parseHex("08090A0B0C0D")),
                Util.parseHex("0001020304050607")),
            8);
    byte[] hostCrypto = GpCrypto.full3DesCbcMac(sEnc, paddedH);
    assertEquals(
        "E87E4C9E45C47F79",
        Util.toHex(Arrays.copyOfRange(hostCrypto, hostCrypto.length - 8, hostCrypto.length)));
  }

  /**
   * Computes the C-MAC for an EXTERNAL AUTHENTICATE APDU carrying the known host cryptogram.
   *
   * <p>This test checks that the retail MAC over the padded 0x84 0x82 APDU header and host
   * cryptogram equals the GPCS known answer.
   *
   * <p>Benefit: this is the value the card verifies, so a mismatch here explains an authentication
   * failure without debugging the whole APDU exchange.
   */
  @Test
  @DisplayName(
      "The EXTERNAL AUTHENTICATE C-MAC over the reference host cryptogram should match AD804734CE044FFC")
  void computesExternalAuthenticateCmac() throws Exception {
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] sMac =
        GpCrypto.des3CbcEncrypt(
            GpCrypto.expand3DesKey(key),
            new byte[8],
            Util.concat(new byte[] {0x01, 0x01, 0x00, 0x01}, new byte[12]));

    // EA plaintext: CLA=0x84, INS=0x82, P1=0x01 (C-MAC, S8), P2=0x00, Lc=0x10
    byte[] hostCrypto = Util.parseHex("E87E4C9E45C47F79");
    byte[] ea = Util.concat(new byte[] {(byte) 0x84, (byte) 0x82, 0x01, 0x00, 0x10}, hostCrypto);
    byte[] padded = GpCrypto.padIso9797Method2(ea, 8);
    byte[] cmac = GpCrypto.retailMac(sMac, new byte[8], padded);
    assertEquals("AD804734CE044FFC", Util.toHex(cmac));
  }

  // FIPS-81 DES KAT — https://csrc.nist.gov/publications/detail/fips/81/archived
  // Used as the single-DES primitive under retail MAC.
  /**
   * Encrypts one 8-byte block with the single-DES primitive used underneath the retail MAC.
   *
   * <p>This test checks the result against the FIPS-81 known-answer vector.
   *
   * <p>Benefit: the single-DES primitive is shared by the SCP02 MAC functions, so this test pins
   * the lowest level and keeps a higher-level failure from being mistaken for a key-derivation
   * problem.
   */
  @Test
  @DisplayName(
      "Single-DES encryption of the FIPS-81 plaintext should match the published ciphertext")
  void encryptsDesEcbVector() throws Exception {
    byte[] key = Util.parseHex("0123456789ABCDEF");
    assertEquals(
        "3FA40E8A984D4815",
        Util.toHex(GpCrypto.desEcbEncrypt(key, Util.parseHex("4E6F772069732074"))));
  }

  /**
   * Applies ISO 9797-1 Method 2 padding to a message that already fills two 8-byte blocks.
   *
   * <p>This test checks that a full third block is appended, with 0x80 in its first byte.
   *
   * <p>Benefit: Method 2 always adds a block, unlike zero padding, so this test prevents an
   * implementation from omitting that block and changing every retail MAC input.
   */
  @Test
  @DisplayName(
      "ISO 9797-1 Method 2 padding of a 16-byte message should add an 8-byte block starting with 0x80")
  void padsIso9797With80() {
    byte[] out = GpCrypto.padIso9797Method2(new byte[16], 8);
    assertEquals(24, out.length);
    assertEquals((byte) 0x80, out[16]);
  }
}
