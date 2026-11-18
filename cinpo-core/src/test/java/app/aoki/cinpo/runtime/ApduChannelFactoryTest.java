package app.aoki.cinpo.runtime;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.runtime.jcardengine.JCardEngineInstallPhase;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApduChannelFactoryTest {

    @Test
    void createInstallPhase_ResolvesTheProfileRuntime() {
        assertTrue(ApduChannelFactory.createInstallPhase(profileFor("jcardengine"))
                instanceof JCardEngineInstallPhase);
        assertTrue(ApduChannelFactory.createInstallPhase(profileFor("jcresim"))
                instanceof InstallPhase);
        assertTrue(ApduChannelFactory.createInstallPhase(profileFor("pcsc"))
                instanceof InstallPhase);
    }

    @Test
    void createInstallPhase_MatchesCaseAndSurroundingWhitespace() {
        assertTrue(ApduChannelFactory.createInstallPhase(profileFor("JcreSim"))
                instanceof InstallPhase);
        assertTrue(ApduChannelFactory.createInstallPhase(profileFor("  PCSC  "))
                instanceof InstallPhase);
    }

    @Test
    void createInstallPhase_UnknownRuntimeFailsWithTheAvailableList() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ApduChannelFactory.createInstallPhase(profileFor("jcardsim")));
        assertTrue(e.getMessage().contains("jcardsim"));
        assertTrue(e.getMessage().contains("available"));
        assertTrue(e.getMessage().contains("pcsc"));
        assertTrue(e.getMessage().contains("Add the matching"));
    }

    private static Profile profileFor(String runtime) {
        return new Profile("test", runtime, testScpConfig());
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
