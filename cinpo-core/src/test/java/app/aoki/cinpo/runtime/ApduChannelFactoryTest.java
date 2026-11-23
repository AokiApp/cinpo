package app.aoki.cinpo.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.runtime.jcardengine.JCardEngineInstallPhase;
import app.aoki.cinpo.util.Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApduChannelFactoryTest {

  /**
   * Creates an install phase for each runtime name the factory accepts.
   *
   * <p>This test checks that jcardengine yields a JCardEngineInstallPhase, and that jcdksim and
   * pcsc yield an InstallPhase.
   *
   * <p>Benefit: a wrong runtime-to-class mapping would otherwise surface only when a task runs, so
   * this test keeps every supported mapping covered.
   */
  @Test
  @DisplayName(
      "Creating an install phase for jcardengine, jcdksim, or pcsc should return a phase for that runtime")
  void resolvesInstallPhaseByRuntime() {
    assertTrue(
        ApduChannelFactory.createInstallPhase(profileFor("jcardengine"))
            instanceof JCardEngineInstallPhase);
    assertTrue(
        ApduChannelFactory.createInstallPhase(profileFor("jcdksim")) instanceof InstallPhase);
    assertTrue(ApduChannelFactory.createInstallPhase(profileFor("pcsc")) instanceof InstallPhase);
  }

  /**
   * Creates an install phase with runtime names that differ in case and carry surrounding spaces.
   *
   * <p>This test checks that the factory normalizes the name before lookup, using JcdkSim and
   * padded PCSC as inputs.
   *
   * <p>Benefit: profile values are written by hand, so this test keeps harmless formatting
   * differences from turning into a failed install.
   */
  @Test
  @DisplayName(
      "Creating an install phase from mixed-case or padded runtime text should still resolve the runtime")
  void normalizesRuntimeName() {
    assertTrue(
        ApduChannelFactory.createInstallPhase(profileFor("JcdkSim")) instanceof InstallPhase);
    assertTrue(
        ApduChannelFactory.createInstallPhase(profileFor("  PCSC  ")) instanceof InstallPhase);
  }

  /**
   * Creates an install phase with the unsupported runtime name jcardsim.
   *
   * <p>This test checks that the factory throws IllegalArgumentException and that the message names
   * the bad value, lists the available runtimes, and tells the user to add a matching
   * implementation.
   *
   * <p>Benefit: jcardsim is a plausible name that users may try, so a precise error here avoids a
   * guess about which runtimes are actually supported.
   */
  @Test
  @DisplayName(
      "Creating an install phase for an unknown runtime should fail and list the available runtimes")
  void rejectsUnknownRuntime() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
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
    return new ScpConfig("scp03", Util.parseHex("A000000151000000"), 16, 0, 1, key, key, key);
  }
}
