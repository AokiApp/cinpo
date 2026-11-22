package app.aoki.cinpo.runtime.jcardengine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.runtime.ApduChannelFactory;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.Test;

/** Verifies that a runtime module contributes exactly its own adapter to discovery. */
class AdapterDiscoveryTest {

  @Test
  void onlyTheDeclaredRuntimeIsDiscoverable() {
    assertTrue(
        ApduChannelFactory.createInstallPhase(profile("jcardengine"))
            instanceof JCardEngineInstallPhase);

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ApduChannelFactory.createInstallPhase(profile("jcdksim")));
    assertTrue(e.getMessage().contains("(available: jcardengine)"));
  }

  private static Profile profile(String runtime) {
    return new Profile("discovery-test", runtime, testScpConfig());
  }

  private static ScpConfig testScpConfig() {
    byte[] key = Util.parseHex("1111111111111111111111111111111111111111111111111111111111111111");
    return new ScpConfig("scp03", Util.parseHex("A000000151000000"), 16, 0, 1, key, key, key);
  }
}
