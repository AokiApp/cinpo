package app.aoki.cinpo.runtime.jcresim;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.runtime.ApduChannelFactory;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a runtime module contributes exactly its own adapter to discovery.
 */
class AdapterDiscoveryTest {

    @Test
    void onlyTheDeclaredRuntimeIsDiscoverable() {
        assertTrue(ApduChannelFactory.createInstallPhase(profile("jcresim"))
                instanceof InstallPhase);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ApduChannelFactory.createInstallPhase(profile("jcardengine")));
        assertTrue(e.getMessage().contains("(available: jcresim)"));
    }

    private static Profile profile(String runtime) {
        return new Profile("discovery-test", runtime, testScpConfig());
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
