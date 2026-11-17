package app.aoki.cinpo.gp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import app.aoki.cinpo.gp.GpCrypto;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

/**
 * SCP03 known-answer tests for the public GpCrypto API surface (AES-CMAC path).
 *
 * <p>Vectors: scll-backend-rustcrypto tests/scp03_kat.rs (docs.rs 1.0.5); engine
 * primitives (RFC 4493, NIST SP 800-38A). Amendment D §4.1.5: KDF counter mode
 * with CMAC PRF, fixed input = '00'×11‖derivation constant || '00' || L (2 bytes) || i (1 byte),
 * context = host challenge(8B) || card challenge(8B).
 *
 * <p>Derivation constants (S-ENC=0x04, S-MAC=0x06, S-RMAC=0x07; card/host cryptogram
 * = 0x00 / 0x01) are cross-checked independently against a pyca/cryptography
 * implementation of the same derivation (EB845BBC.../94D914.../F034ED.../114F6BC5.../FAFA93C2...
 * for S8; 313082B4.../1F3975... for S16; 86C8BD65.../692443... for pseudo-random
 * challenges).
 */
class Scp03CryptoFunctionsTest {

    @Test
    void cmac_rfc4493Examples() throws Exception {
        byte[] key = Util.parseHex("2b7e151628aed2a6abf7158809cf4f3c");

        // Empty message
        assertEquals("BB1D6929E95937287FA37D129B756746",
                Util.toHex(GpCrypto.cmac(key, new byte[0])));
        // One block
        assertEquals("070A16B46B4D4144F79BDD9DD04A287C",
                Util.toHex(GpCrypto.cmac(key,
                        Util.parseHex("6bc1bee22e409f96e93d7e117393172a"))));
        // Two and a half blocks (example 3)
        assertEquals("DFA66747DE9AE63030CA32611497C827",
                Util.toHex(GpCrypto.cmac(key,
                        Util.parseHex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411"))));
        // Four blocks (example 4)
        assertEquals("51F0BEBF7E3B9D92FC49741779363CFE",
                Util.toHex(GpCrypto.cmac(key,
                        Util.parseHex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710"))));
    }

    // NIST SP 800-38A §F.2.1 — AES-128 CBC
    @Test
    void aesCbcEncrypt_nistF21Vector() throws Exception {
        byte[] key = Util.parseHex("2b7e151628aed2a6abf7158809cf4f3c");
        byte[] iv  = Util.parseHex("000102030405060708090a0b0c0d0e0f");
        byte[] pt  = Util.parseHex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710");
        assertEquals("7649ABAC8119B246CEE98E9B12E9197D5086CB9B507219EE95DB113A917678B273BED6B8E3C1743B7116E69E222295163FF1CAA1681FAC09120ECA307586E1A7",
                Util.toHex(GpCrypto.aesCbcEncrypt(key, iv, pt)));
    }

    @Test
    void sessionKeyDerivation_matchesReference() throws Exception {
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] context = Util.concat(Util.parseHex("0001020304050607"),
                Util.parseHex("08090A0B0C0D0E0F"));

        assertEquals("EB845BBC703969A9B312A5F8E4834AA2",
                Util.toHex(kdf(key, 0x04, 0x0080, context)));
        assertEquals("94D9141C5E50A39EF3939B9A4616C910",
                Util.toHex(kdf(key, 0x06, 0x0080, context)));
        assertEquals("F034ED3222D3466EE1531F3FA3561DEF",
                Util.toHex(kdf(key, 0x07, 0x0080, context)));
    }

    @Test
    void cardAndHostCryptograms_matchReference() throws Exception {
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] context = Util.concat(Util.parseHex("0001020304050607"),
                Util.parseHex("08090A0B0C0D0E0F"));
        byte[] sMac = kdf(key, 0x06, 0x0080, context);

        // L = 0x0040 (64 bits) for cryptograms — first 8 bytes of the CMAC
        assertEquals("114F6BC5052C5228",
                Util.toHex(Arrays.copyOfRange(kdf(sMac, 0x00, 0x0040, context), 0, 8)));
        assertEquals("FAFA93C2EDE62463",
                Util.toHex(Arrays.copyOfRange(kdf(sMac, 0x01, 0x0040, context), 0, 8)));
    }

    // S16 mode (Amendment D §6.2; 16-byte challenges/cryptograms)
    @Test
    void s16Cryptograms_matchReference() throws Exception {
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] context = Util.concat(Util.parseHex("000102030405060708090A0B0C0D0E0F"),
                Util.parseHex("101112131415161718191A1B1C1D1E1F"));
        byte[] sMac = kdf(key, 0x06, 0x0080, context);

        // L = 0x0080 (128 bits) — full 16-byte cryptograms
        assertEquals("313082B4105AE33D7C2D323A1E5CD6FF",
                Util.toHex(kdf(sMac, 0x01, 0x0080, context)));
        assertEquals("1F3975EB7C6D743F38BB5842328732A1",
                Util.toHex(kdf(sMac, 0x00, 0x0080, context)));
    }

    // Amendment D §6.2.2.1: pseudo-random card challenge, context = seq‖invoker_AID
    @Test
    void pseudoRandomCardChallenge_s8_matchesReference() throws Exception {
        // S8 (field = 8 bytes): L = 0x0040, 64 bits
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] context = Util.concat(new byte[]{0x00, 0x00, 0x01},
                Util.parseHex("A000000151000000"));
        assertEquals("86C8BD65FA1044EE",
                Util.toHex(Arrays.copyOfRange(kdf(key, 0x02, 0x0040, context), 0, 8)));
    }

    @Test
    void pseudoRandomCardChallenge_s16_matchesReference() throws Exception {
        // S16 (field = 16 bytes): L = 0x0080, 128 bits
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] context = Util.concat(new byte[]{0x00, 0x00, 0x01},
                Util.parseHex("A000000151000000"));
        assertEquals("6924434C956736C8EE6122E8A3FC612F",
                Util.toHex(kdf(key, 0x02, 0x0080, context)));
    }

    // ── helper: Amd D §4.1.5 KDF (counter mode, CMAC PRF) — used only via public API ──

    private static byte[] kdf(byte[] baseKey, int constant, int lengthBits, byte[] context) throws Exception {
        byte[] input = new byte[12 + 1 + 2 + 1];
        System.arraycopy(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) constant},
                0, input, 0, 12);
        input[12] = 0x00;                    // separation indicator
        input[13] = (byte) (lengthBits >> 8); // L (2 bytes, big-endian)
        input[14] = (byte) lengthBits;
        input[15] = 0x01;                    // i = 1
        return GpCrypto.cmac(baseKey, Util.concat(input, context));
    }
}
