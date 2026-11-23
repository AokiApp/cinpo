package app.aoki.cinpo.runtime.pcsc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
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
   * <p>Verifies that discovery returns the pcsc adapter for "pcsc" and rejects "jcdksim" with an
   * error that names the available runtime.
   *
   * <p>Benefit: without this test, a missing pcsc registration could load a simulator adapter in a
   * card run and hide the misconfiguration.
   */
  @Test
  @DisplayName("Only the declared runtime adapter is discoverable")
  void onlyDeclaredRuntimeDiscoverable() {
    assertTrue(ApduChannelFactory.createInstallPhase(profile("pcsc")) instanceof InstallPhase);

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ApduChannelFactory.createInstallPhase(profile("jcdksim")));
    assertTrue(e.getMessage().contains("(available: pcsc)"));
  }

  private static Profile profile(String runtime) {
    return new Profile("discovery-test", runtime, testScpConfig());
  }

  private static ScpConfig testScpConfig() {
    byte[] key = Util.parseHex("1111111111111111111111111111111111111111111111111111111111111111");
    return new ScpConfig("scp03", Util.parseHex("A000000151000000"), 16, 0, 1, key, key, key);
  }
}
