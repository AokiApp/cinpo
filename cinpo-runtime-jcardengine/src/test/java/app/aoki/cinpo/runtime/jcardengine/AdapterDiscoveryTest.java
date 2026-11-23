package app.aoki.cinpo.runtime.jcardengine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.runtime.ApduChannelFactory;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies that a runtime module contributes exactly its own adapter to discovery. */
class AdapterDiscoveryTest {

  /**
   * Creates install phases for the declared runtime and for a runtime that is not on the classpath,
   * then checks which adapter discovery resolves in each case.
   *
   * <p>Verifies that discovery returns the jcardengine adapter for "jcardengine" and rejects
   * "jcdksim" with an error that names the available runtime.
   *
   * <p>Benefit: without this test, a missing jcardengine registration could silently load another
   * runtime's adapter and the failure message would not tell users which runtime is installed.
   */
  @Test
  @DisplayName("Only the declared runtime adapter is discoverable")
  void onlyDeclaredRuntimeDiscoverable() {
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
