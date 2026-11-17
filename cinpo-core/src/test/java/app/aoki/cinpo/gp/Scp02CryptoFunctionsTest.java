package app.aoki.cinpo.gp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.aoki.cinpo.gp.GpCrypto;
import app.aoki.cinpo.util.Util;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * SCP02 known-answer tests for the public GpCrypto API surface.
 *
 * <p>Vectors: scll-backend-rustcrypto tests/scp02_kat.rs (docs.rs 1.0.5).
 * GPCS v2.3.1 Appendix E (i=0x55):
 * <ul>
 *   <li>S-ENC / S-MAC / S-RMAC / S-DEK derivation constants 0x0182 / 0x0101 / 0x0102 / 0x0181</li>
 *   <li>HOST challenge 0001020304050607, CARD challenge 08090A0B0C0D, SEQ 0001</li>
 *   <li>Base 3DES key 404142434445464748494A4B4C4D4E4F (as ENC/MAC/DEK)</li>
 * </ul>
 *
 * <p>Independently cross-checked against a pyca/cryptography reference implementation
 * of the same scheme (matches all five derived values), so these values are not the
 * output of any single implementation.
 */
class Scp02CryptoFunctionsTest {

    @Test
    void expand3DesKey_doublesKeyEde2() throws Exception {
        byte[] expanded = GpCrypto.expand3DesKey(Util.parseHex("404142434445464748494A4B4C4D4E4F"));
        assertEquals("404142434445464748494A4B4C4D4E4F4041424344454647", Util.toHex(expanded));
    }

    @Test
    void sessionKeyDerivation_matchesReference() throws Exception {
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] iv = new byte[8];

        // S-ENC: derivation constant 0x0182 (GPCS §E.4.1)
        byte[] sEnc = GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(key), iv,
                Util.concat(new byte[]{0x01, (byte) 0x82, 0x00, 0x01}, new byte[12]));
        assertEquals("25C9794A1205FF244F5FA0378D2F8D59", Util.toHex(sEnc));

        // S-MAC: derivation constant 0x0101
        byte[] sMac = GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(key), iv,
                Util.concat(new byte[]{0x01, 0x01, 0x00, 0x01}, new byte[12]));
        assertEquals("9BED98891580C3B245FE9EC58BFA8D2A", Util.toHex(sMac));

        // S-RMAC: derivation constant 0x0102
        byte[] sRmac = GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(key), iv,
                Util.concat(new byte[]{0x01, 0x02, 0x00, 0x01}, new byte[12]));
        assertEquals("8EB6CF25BA4DECD820BF4E9FA616C50A", Util.toHex(sRmac));

        // S-DEK: derivation constant 0x0181
        byte[] sDek = GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(key), iv,
                Util.concat(new byte[]{0x01, (byte) 0x81, 0x00, 0x01}, new byte[12]));
        assertEquals("0E51FDF196141F227A57BD154012FD39", Util.toHex(sDek));
    }

    @Test
    void cardAndHostCryptograms_matchReference() throws Exception {
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] sEnc = GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(key), new byte[8],
                Util.concat(new byte[]{0x01, (byte) 0x82, 0x00, 0x01}, new byte[12]));

        // card cryptogram: full 3DES-CBC-MAC over ISO 9797-1 Method 2 padded
        // host challenge(8) || sequence counter(2) || card challenge(6)
        byte[] padded = GpCrypto.padIso9797Method2(
                Util.concat(Util.parseHex("0001020304050607"), Util.concat(new byte[]{0x00, 0x01},
                        Util.parseHex("08090A0B0C0D"))), 8);
        byte[] cardCrypto = GpCrypto.full3DesCbcMac(sEnc, padded);
        assertEquals("9D8DA672F05C4E45",
                Util.toHex(Arrays.copyOfRange(cardCrypto, cardCrypto.length - 8, cardCrypto.length)));

        // host cryptogram: card challenge || host challenge (the reverse order)
        byte[] paddedH = GpCrypto.padIso9797Method2(
                Util.concat(Util.concat(new byte[]{0x00, 0x01}, Util.parseHex("08090A0B0C0D")),
                        Util.parseHex("0001020304050607")), 8);
        byte[] hostCrypto = GpCrypto.full3DesCbcMac(sEnc, paddedH);
        assertEquals("E87E4C9E45C47F79",
                Util.toHex(Arrays.copyOfRange(hostCrypto, hostCrypto.length - 8, hostCrypto.length)));
    }

    @Test
    void externalAuthenticateCmac_matchesReference() throws Exception {
        byte[] key = Util.parseHex("404142434445464748494A4B4C4D4E4F");
        byte[] sMac = GpCrypto.des3CbcEncrypt(GpCrypto.expand3DesKey(key), new byte[8],
                Util.concat(new byte[]{0x01, 0x01, 0x00, 0x01}, new byte[12]));

        // EA plaintext: CLA=0x84, INS=0x82, P1=0x01 (C-MAC, S8), P2=0x00, Lc=0x10
        byte[] hostCrypto = Util.parseHex("E87E4C9E45C47F79");
        byte[] ea = Util.concat(new byte[]{(byte) 0x84, (byte) 0x82, 0x01, 0x00, 0x10}, hostCrypto);
        byte[] padded = GpCrypto.padIso9797Method2(ea, 8);
        byte[] cmac = GpCrypto.retailMac(sMac, new byte[8], padded);
        assertEquals("AD804734CE044FFC", Util.toHex(cmac));
    }

    @Test
    void padIso9797Method2_padsWith80() {
        byte[] out = GpCrypto.padIso9797Method2(new byte[16], 8);
        assertEquals(24, out.length);
        assertEquals((byte) 0x80, out[16]);
    }
}
