package app.aoki.cinpo.gp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.aoki.cinpo.util.Util;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SCP03 known-answer tests for the public GpCrypto API surface (AES-CMAC path).
 *
 * <p>Vectors: scll-backend-rustcrypto tests/scp03_kat.rs (docs.rs 1.0.5); engine primitives (RFC
 * 4493, NIST SP 800-38A). Amendment D §4.1.5: KDF counter mode with CMAC PRF, fixed input =
 * '00'×11‖derivation constant || '00' || L (2 bytes) || i (1 byte), context = host challenge(8B) ||
 * card challenge(8B).
 *
 * <p>Derivation constants (S-ENC=0x04, S-MAC=0x06, S-RMAC=0x07; card/host cryptogram = 0x00 / 0x01)
 * are cross-checked independently against a pyca/cryptography implementation of the same derivation
 * (EB845BBC.../94D914.../F034ED.../114F6BC5.../FAFA93C2... for S8; 313082B4.../1F3975... for S16;
 * 86C8BD65.../692443... for pseudo-random challenges).
 */
class Scp03CryptoFunctionsTest {

  /**
   * Computes AES-CMAC with the RFC 4493 key over messages of zero, one, two and a half, and four
   * blocks, and compares each result with the published digest.
   *
   * <p>This checks the CMAC implementation against all four published examples, including the
   * subkey derivation used for the incomplete final block.
   *
   * <p>Benefit: a CMAC error would corrupt every key derived from it, so this test catches the
   * problem at its source.
   */
  @DisplayName("AES-CMAC matches the four RFC 4493 known-answer vectors.")
  @Test
  void matchesRfcCmacVectors() throws Exception {
    byte[] key = Util.parseHex("2b7e151628aed2a6abf7158809cf4f3c");

    // Empty message
    assertEquals("BB1D6929E95937287FA37D129B756746", Util.toHex(GpCrypto.cmac(key, new byte[0])));
    // One block
    assertEquals(
        "070A16B46B4D4144F79BDD9DD04A287C",
        Util.toHex(GpCrypto.cmac(key, Util.parseHex("6bc1bee22e409f96e93d7e117393172a"))));
    // Two and a half blocks (example 3)
    assertEquals(
        "DFA66747DE9AE63030CA32611497C827",
        Util.toHex(
            GpCrypto.cmac(
                key,
                Util.parseHex(
                    "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411"))));
    // Four blocks (example 4)
    assertEquals(
        "51F0BEBF7E3B9D92FC49741779363CFE",
        Util.toHex(
            GpCrypto.cmac(
                key,
                Util.parseHex(
                    "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710"))));
  }

  // NIST SP 800-38A §F.2.1 — AES-128 CBC
  /**
   * Encrypts four plaintext blocks with AES-128 CBC using the NIST SP 800-38A key and IV and
   * compares the ciphertext with the published vector.
   *
   * <p>This checks the engine primitive used by the SCP03 implementation against a standard
   * known-answer test.
   *
   * <p>Benefit: a cipher regression is separated from errors in the key derivation built on top of
   * it.
   */
  @DisplayName("AES-CBC encryption matches the NIST SP 800-38A F.2.1 vector.")
  @Test
  void matchesAesCbcVector() throws Exception {
    byte[] key = Util.parseHex("2b7e151628aed2a6abf7158809cf4f3c");
    byte[] iv = Util.parseHex("000102030405060708090a0b0c0d0e0f");
    byte[] pt =
        Util.parseHex(
            "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710");
    assertEquals(
        "7649ABAC8119B246CEE98E9B12E9197D5086CB9B507219EE95DB113A917678B273BED6B8E3C1743B7116E69E222295163FF1CAA1681FAC09120ECA307586E1A7",
        Util.toHex(GpCrypto.aesCbcEncrypt(key, iv, pt)));
  }

  /**
   * Derives S-ENC, S-MAC and S-RMAC from a fixed base key and host and card challenges, and
   * compares each result with an independently computed value.
   *
   * <p>This checks the counter-mode KDF with the CMAC PRF, including the constants 0x04, 0x06 and
   * 0x07 and the 0x0080 length field.
   *
   * <p>Benefit: a second implementation catches a wrong constant or byte order before the keys are
   * used to protect real APDUs.
   */
  @DisplayName("Session key derivation matches the reference for S-ENC, S-MAC and S-RMAC.")
  @Test
  void derivesSessionKeys() throws Exception {
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] context =
        Util.concat(Util.parseHex("0001020304050607"), Util.parseHex("08090A0B0C0D0E0F"));

    assertEquals("EB845BBC703969A9B312A5F8E4834AA2", Util.toHex(kdf(key, 0x04, 0x0080, context)));
    assertEquals("94D9141C5E50A39EF3939B9A4616C910", Util.toHex(kdf(key, 0x06, 0x0080, context)));
    assertEquals("F034ED3222D3466EE1531F3FA3561DEF", Util.toHex(kdf(key, 0x07, 0x0080, context)));
  }

  /**
   * Derives the card cryptogram and the host cryptogram from the S-MAC key with constants 0x00 and
   * 0x01, and compares the first eight CMAC bytes with the reference values.
   *
   * <p>This checks the S8 cryptogram derivation with L set to 0x0040 and the assignment of the
   * constants to the card and host roles.
   *
   * <p>Benefit: a swapped card/host constant is caught by a reference value instead of surfacing
   * during authentication with a card.
   */
  @DisplayName("Card and host cryptograms match the reference in S8 mode.")
  @Test
  void derivesCardAndHostCryptograms() throws Exception {
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] context =
        Util.concat(Util.parseHex("0001020304050607"), Util.parseHex("08090A0B0C0D0E0F"));
    byte[] sMac = kdf(key, 0x06, 0x0080, context);

    // L = 0x0040 (64 bits) for cryptograms — first 8 bytes of the CMAC
    assertEquals(
        "114F6BC5052C5228", Util.toHex(Arrays.copyOfRange(kdf(sMac, 0x00, 0x0040, context), 0, 8)));
    assertEquals(
        "FAFA93C2EDE62463", Util.toHex(Arrays.copyOfRange(kdf(sMac, 0x01, 0x0040, context), 0, 8)));
  }

  // S16 mode (Amendment D §6.2; 16-byte challenges/cryptograms)
  /**
   * Derives both 16-byte cryptograms in S16 mode from the S-MAC key and compares them with the
   * reference values.
   *
   * <p>This checks the Amendment D S16 variant, where L is 0x0080 and the full CMAC output is used
   * instead of its first eight bytes.
   *
   * <p>Benefit: protects S16 cards, where truncating the cryptogram would break authentication.
   */
  @DisplayName("S16 mode cryptograms match the reference.")
  @Test
  void derivesS16Cryptograms() throws Exception {
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] context =
        Util.concat(
            Util.parseHex("000102030405060708090A0B0C0D0E0F"),
            Util.parseHex("101112131415161718191A1B1C1D1E1F"));
    byte[] sMac = kdf(key, 0x06, 0x0080, context);

    // L = 0x0080 (128 bits) — full 16-byte cryptograms
    assertEquals("313082B4105AE33D7C2D323A1E5CD6FF", Util.toHex(kdf(sMac, 0x01, 0x0080, context)));
    assertEquals("1F3975EB7C6D743F38BB5842328732A1", Util.toHex(kdf(sMac, 0x00, 0x0080, context)));
  }

  // Amendment D §6.2.2.1: pseudo-random card challenge, context = seq‖invoker_AID
  /**
   * Derives the pseudo-random card cryptogram with a sequence number and invoker AID as context,
   * and compares the first eight bytes with the reference value.
   *
   * <p>This checks the Amendment D branch where the card generates the challenge rather than the
   * host, with L set to 0x0040 for S8 mode.
   *
   * <p>Benefit: covers the authentication path used when the card, not the host, chooses the
   * challenge.
   */
  @DisplayName("S8 pseudo-random card challenge derivation matches the reference.")
  @Test
  void derivesS8PseudoRandomCryptogram() throws Exception {
    // S8 (field = 8 bytes): L = 0x0040, 64 bits
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] context = Util.concat(new byte[] {0x00, 0x00, 0x01}, Util.parseHex("A000000151000000"));
    assertEquals(
        "86C8BD65FA1044EE", Util.toHex(Arrays.copyOfRange(kdf(key, 0x02, 0x0040, context), 0, 8)));
  }

  /**
   * Derives the pseudo-random card cryptogram in S16 mode with a sequence number and invoker AID as
   * context, and compares the full 16 bytes with the reference value.
   *
   * <p>This checks the same Amendment D branch with L set to 0x0080 and no truncation of the CMAC
   * output.
   *
   * <p>Benefit: covers S16 cards that generate their own challenge, where a wrong length field
   * would produce a different cryptogram.
   */
  @DisplayName("S16 pseudo-random card challenge derivation matches the reference.")
  @Test
  void derivesS16PseudoRandomCryptogram() throws Exception {
    // S16 (field = 16 bytes): L = 0x0080, 128 bits
    byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
    byte[] context = Util.concat(new byte[] {0x00, 0x00, 0x01}, Util.parseHex("A000000151000000"));
    assertEquals("6924434C956736C8EE6122E8A3FC612F", Util.toHex(kdf(key, 0x02, 0x0080, context)));
  }

  // ── helper: Amd D §4.1.5 KDF (counter mode, CMAC PRF) — used only via public API ──

  private static byte[] kdf(byte[] baseKey, int constant, int lengthBits, byte[] context)
      throws Exception {
    byte[] input = new byte[12 + 1 + 2 + 1];
    System.arraycopy(
        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) constant}, 0, input, 0, 12);
    input[12] = 0x00; // separation indicator
    input[13] = (byte) (lengthBits >> 8); // L (2 bytes, big-endian)
    input[14] = (byte) lengthBits;
    input[15] = 0x01; // i = 1
    return GpCrypto.cmac(baseKey, Util.concat(input, context));
  }
}
