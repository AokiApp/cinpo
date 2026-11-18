package app.aoki.cinpo.runtime;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApduChannelFactoryTest {

    @Test
    void registered_ContainsBuiltInRuntimes() {
        assertTrue(ApduChannelFactory.registered().stream()
                .map(RuntimeAdapter::name)
                .anyMatch("jcresim"::equalsIgnoreCase));
        assertTrue(ApduChannelFactory.registered().stream()
                .map(RuntimeAdapter::name)
                .anyMatch("jcardengine"::equalsIgnoreCase));
        assertTrue(ApduChannelFactory.registered().stream()
                .map(RuntimeAdapter::name)
                .anyMatch("pcsc"::equalsIgnoreCase));
    }

    @Test
    void forName_IsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals("pcsc", ApduChannelFactory.forName("  PCSC  ").name());
        assertEquals("jcardengine", ApduChannelFactory.forName("JCARDENGINE").name());
        assertEquals("jcresim", ApduChannelFactory.forName("JcreSim").name());
    }

    @Test
    void forName_UnknownRuntimeFailsClearly() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> ApduChannelFactory.forName("jcardsim"));
        assertTrue(e.getMessage().contains("jcardsim"));
    }

    @Test
    void forProfile_ResolvesRuntime() {
        Profile profile = new Profile("test", "pcsc", testScpConfig());
        assertEquals("pcsc", ApduChannelFactory.forProfile(profile).name());
    }

    private static ScpConfig testScpConfig() {
        byte[] key = Util.parseHex("1111111111111111111111111111111111111111111111111111111111111111");
        return new ScpConfig(
                "scp03",
                Util.parseHex("A000000151000000"),
                16,
                0,
                1,
                key,
                key,
                key
        );
    }
}
