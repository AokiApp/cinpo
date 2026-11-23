package app.aoki.cinpo.orchestrator;

import static org.junit.jupiter.api.Assertions.*;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletEntry;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import app.aoki.cinpo.orchestrator.phase.InitPhase;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.orchestrator.phase.TaskKindPhase;
import app.aoki.cinpo.runtime.ApduChannelFactory;
import app.aoki.cinpo.runtime.jcardengine.JCardEngineInstallPhase;
import app.aoki.cinpo.task.TaskArguments;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for orchestrator phase execution based on CLI argument combinations.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>Phases are executed in the correct order
 *   <li>The correct phases are selected based on CLI flags (--test, --task, --skip-install)
 *   <li>Phase execution can be tracked without requiring actual card/simulator connections
 * </ul>
 */
class PhaseExecutionTest {

  private Profile testProfile;
  private AppletManifest testManifest;

  @BeforeEach
  void setUp() {
    // Create minimal test profile and manifest
    ScpConfig scpConfig =
        new ScpConfig(
            "scp02",
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05},
            0x00,
            0x00,
            0x03,
            new byte[] {
              0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D,
              0x4E, 0x4F
            },
            new byte[] {
              0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D,
              0x4E, 0x4F
            },
            new byte[] {
              0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D,
              0x4E, 0x4F
            });

    testProfile = new Profile("test-profile", "jcdksim", scpConfig);

    AppletEntry appletEntry =
        new AppletEntry(
            "test-applet",
            "test.TestApplet",
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x01},
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x00, 0x00, 0x00, 0x01},
            new byte[] {0x00});

    testManifest =
        new AppletManifest(
            "test.base",
            "26.0",
            "3.0.4",
            "test.package",
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05},
            "1.0",
            List.of(appletEntry),
            List.of());
  }

  /**
   * Runs the orchestrator with a supplied list of tracking phases and records their execution.
   *
   * <p>Verifies that init, install, and provision tracking phases execute in the order they were
   * supplied. This isolates the orchestrator's ordering loop from CLI phase selection, so a
   * reordering regression is reported by this test rather than by a CLI-level test.
   */
  @DisplayName("Supplied init, install, and provision phases execute in that order")
  @Test
  void runsDefaultPhasesInOrder() throws IOException {
    // Given: orchestrator with default phases (init → install → provision)
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Init", executedPhases),
            trackingPhase("Install", executedPhases),
            trackingPhase("TaskKind[provision]", executedPhases));

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    orchestrator.run();

    // Then: verify all phases executed in order
    assertEquals(List.of("Init", "Install", "TaskKind[provision]"), executedPhases);
  }

  /**
   * Runs the orchestrator with four supplied tracking phases, including provision and test.
   *
   * <p>Verifies that the test phase follows provisioning and that neither the install nor the
   * provision phase is dropped. This keeps the expected phase sequence for a run that includes
   * testing visible without depending on CLI flag parsing.
   */
  @DisplayName("A supplied provision phase is followed by a test phase, with install still present")
  @Test
  void runsTestPhaseWithFlag() throws IOException {
    // Given: orchestrator with --test flag (init → install → provision → test)
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Init", executedPhases),
            trackingPhase("Install", executedPhases),
            trackingPhase("TaskKind[provision]", executedPhases),
            trackingPhase("TaskKind[test]", executedPhases));

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    orchestrator.run();

    // Then: verify provision and test phases both executed
    assertEquals(
        List.of("Init", "Install", "TaskKind[provision]", "TaskKind[test]"), executedPhases);
  }

  /**
   * Runs the orchestrator with a supplied tracking phase named for a custom task kind.
   *
   * <p>Verifies that the custom phase executes after install. This shows that the orchestrator runs
   * any supplied phase, including one named for a task kind chosen outside the CLI defaults.
   */
  @DisplayName("A supplied custom task-kind phase runs after the install phase")
  @Test
  void runsCustomTaskKindPhase() throws IOException {
    // Given: orchestrator with --task=custom (init → install → custom)
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Init", executedPhases),
            trackingPhase("Install", executedPhases),
            trackingPhase("TaskKind[custom]", executedPhases));

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    orchestrator.run();

    // Then: verify custom task kind executed
    assertEquals(List.of("Init", "Install", "TaskKind[custom]"), executedPhases);
  }

  /**
   * Runs the orchestrator with two supplied tracking phases named for task kinds.
   *
   * <p>Verifies that foo runs before bar. This fixes the order of the supplied phase list as the
   * contract for repeated task kinds, independently of how the CLI collects those kinds.
   */
  @DisplayName("Two supplied task-kind phases run in the order they were supplied")
  @Test
  void runsTaskKindsInOrder() throws IOException {
    // Given: orchestrator with --task=foo --task=bar (init → install → foo → bar)
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Init", executedPhases),
            trackingPhase("Install", executedPhases),
            trackingPhase("TaskKind[foo]", executedPhases),
            trackingPhase("TaskKind[bar]", executedPhases));

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    orchestrator.run();

    // Then: verify task kinds executed in specified order
    assertEquals(List.of("Init", "Install", "TaskKind[foo]", "TaskKind[bar]"), executedPhases);
  }

  /**
   * Runs the orchestrator with a supplied phase list that has no install phase.
   *
   * <p>Verifies that init and provision still run and that no install phase appears in the
   * execution log. This separates the orchestrator's behavior on a shortened phase list from the
   * CLI flag that produces that list.
   */
  @DisplayName("A supplied phase list without install runs init and provision only")
  @Test
  void skipsInstallPhase() throws IOException {
    // Given: orchestrator with --skip-install (init → provision only)
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Init", executedPhases),
            trackingPhase("TaskKind[provision]", executedPhases));

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    orchestrator.run();

    // Then: verify install phase was not executed
    assertEquals(List.of("Init", "TaskKind[provision]"), executedPhases);
    assertFalse(executedPhases.contains("Install"));
  }

  /**
   * Runs four named phases in a fixed order and records when each one executes.
   *
   * <p>Verifies that the orchestrator preserves the order of the phase list it was given. This
   * separates ordering from phase selection, so a regression in the loop that drives phases is
   * diagnosed by a focused failure.
   */
  @DisplayName("The orchestrator runs phases in the order they were supplied")
  @Test
  void preservesPhaseOrder() throws IOException {
    // Given: orchestrator with phases in specific order
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Phase1", executedPhases),
            trackingPhase("Phase2", executedPhases),
            trackingPhase("Phase3", executedPhases),
            trackingPhase("Phase4", executedPhases));

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    orchestrator.run();

    // Then: verify exact execution order
    assertEquals(List.of("Phase1", "Phase2", "Phase3", "Phase4"), executedPhases);
  }

  /**
   * Runs a phase that reads the task arguments from the orchestrator and asserts on them.
   *
   * <p>Verifies that the arguments passed to the constructor are reachable from the phase and
   * report the {@code --flag} option as present. This is the contract task phases rely on to detect
   * which CLI flags were supplied at execution time.
   */
  @DisplayName("The orchestrator passes configured task arguments to each phase")
  @Test
  void passesTaskArgumentsToPhase() throws IOException {
    // Given: orchestrator with task arguments
    TaskArguments taskArgs = new TaskArguments(List.of("--flag", "value"));
    List<Phase> phases =
        List.of(orchestrator -> {
          // Verify task arguments are accessible
          assertEquals(taskArgs, orchestrator.getTaskArguments());
          assertFalse(orchestrator.getTaskArguments().isEmpty());
          assertTrue(orchestrator.getTaskArguments().hasFlag("--flag"));
        });

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true, taskArgs);

    // When: run orchestrator
    // Then: phase assertions verify task arguments
    orchestrator.run();
  }

  /**
   * Runs a phase that inspects the default task arguments of an orchestrator built without them.
   *
   * <p>Verifies that the default is an empty, non-null argument set. This catches a regression
   * where a task phase would receive null and fail at runtime instead of seeing a harmless empty
   * value.
   */
  @DisplayName("Without explicit task arguments, the orchestrator supplies empty arguments")
  @Test
  void defaultsTaskArgumentsToEmpty() throws IOException {
    // Given: orchestrator without explicit task arguments
    List<Phase> phases =
        List.of(orchestrator -> {
          // Verify default is empty
          assertTrue(orchestrator.getTaskArguments().isEmpty());
        });

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    // Then: phase assertions verify empty arguments
    orchestrator.run();
  }

  /**
   * Runs an orchestrator whose second phase throws, with a third phase behind it.
   *
   * <p>Verifies that the IOException propagates and the third phase never executes. This is the
   * failure contract: a half-installed card must not be modified further after a phase reports an
   * error.
   */
  @DisplayName("A failing phase stops the orchestrator before later phases run")
  @Test
  void stopsAfterPhaseFailure() {
    // Given: orchestrator with failing phase
    List<String> executedPhases = new ArrayList<>();
    List<Phase> phases =
        List.of(
            trackingPhase("Phase1", executedPhases),
            orchestrator -> {
              executedPhases.add("FailingPhase");
              throw new IOException("Simulated failure");
            },
            trackingPhase("Phase3", executedPhases) // Should not execute
            );

    Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

    // When: run orchestrator
    // Then: execution stops at failing phase
    assertThrows(IOException.class, orchestrator::run);
    assertEquals(List.of("Phase1", "FailingPhase"), executedPhases);
    assertFalse(executedPhases.contains("Phase3"));
  }

  /**
   * Integration test simulating the actual CLI phase construction logic. This mirrors
   * WriteCommand.buildPhaseList() behavior.
   *
   * <p>Builds the default CLI phase list and checks its types. Verifies that it contains exactly
   * InitPhase, InstallPhase, and TaskKindPhase in that order. This keeps the mirror of
   * WriteCommand.buildPhaseList() aligned with the default run path.
   */
  @DisplayName("Default CLI construction builds init, install, and provision phases")
  @Test
  void buildsDefaultCliPhases() {
    // Given: CLI default behavior
    List<Phase> phases = buildPhasesLikeCli("jcdksim", false, List.of(), false);

    // Then: verify correct phase types and order
    assertEquals(3, phases.size());
    assertTrue(phases.get(0) instanceof InitPhase);
    assertTrue(phases.get(1) instanceof InstallPhase);
    assertTrue(phases.get(2) instanceof TaskKindPhase);
  }

  /**
   * Builds the CLI phase list with the --test option and checks its phase types.
   *
   * <p>Verifies that the list has four entries: init, install, and two TaskKindPhase instances. The
   * assertions check the count and types only; they do not distinguish which task kind each phase
   * carries. This keeps the phase shape of a test-enabled build observable.
   */
  @DisplayName(
      "CLI construction with --test builds four phases: init, install, and two task-kind phases")
  @Test
  void buildsCliTestPhases() {
    // Given: CLI with --test flag
    List<Phase> phases = buildPhasesLikeCli("jcdksim", true, List.of(), false);

    // Then: verify provision and test phases
    assertEquals(4, phases.size());
    assertTrue(phases.get(0) instanceof InitPhase);
    assertTrue(phases.get(1) instanceof InstallPhase);
    assertTrue(phases.get(2) instanceof TaskKindPhase);
    assertTrue(phases.get(3) instanceof TaskKindPhase);
  }

  /**
   * Builds the CLI phase list with two --task values and checks its phase types.
   *
   * <p>Verifies that init and install are followed by two TaskKindPhase instances. The assertions
   * check the count and types only; they do not verify which kind each phase carries. This keeps
   * the phase shape of a multi-task build observable.
   */
  @DisplayName(
      "CLI construction with two task kinds builds init, install, and two task-kind phases")
  @Test
  void buildsCliCustomTaskPhases() {
    // Given: CLI with --task=foo --task=bar
    List<Phase> phases = buildPhasesLikeCli("jcdksim", false, List.of("foo", "bar"), false);

    // Then: verify custom task phases
    assertEquals(4, phases.size());
    assertTrue(phases.get(0) instanceof InitPhase);
    assertTrue(phases.get(1) instanceof InstallPhase);
    assertTrue(phases.get(2) instanceof TaskKindPhase);
    assertTrue(phases.get(3) instanceof TaskKindPhase);
  }

  /**
   * Builds the CLI phase list with --skip-install and checks its phase types.
   *
   * <p>Verifies that only InitPhase and TaskKindPhase remain and no InstallPhase is present. This
   * ensures the flag is applied while constructing the list, not by skipping work inside an install
   * phase.
   */
  @DisplayName("CLI construction with --skip-install omits the install phase")
  @Test
  void buildsCliPhasesWithoutInstall() {
    // Given: CLI with --skip-install
    List<Phase> phases = buildPhasesLikeCli("jcdksim", false, List.of("provision"), true);

    // Then: verify install phase is skipped
    assertEquals(2, phases.size());
    assertTrue(phases.get(0) instanceof InitPhase);
    assertTrue(phases.get(1) instanceof TaskKindPhase);
    assertFalse(phases.stream().anyMatch(p -> p instanceof InstallPhase));
  }

  /**
   * Builds the CLI phase list for the jcardengine runtime and checks the install phase type.
   *
   * <p>Verifies that the list uses JCardEngineInstallPhase instead of the generic InstallPhase.
   * This protects runtime-specific installation, which would otherwise install the wrong component
   * set on that runtime.
   */
  @DisplayName(
      "CLI construction with the jcardengine runtime uses the dedicated JCardEngine install phase")
  @Test
  void usesRuntimeSpecificInstallPhase() {
    List<Phase> phases = buildPhasesLikeCli("jcardengine", false, List.of(), false);

    assertEquals(3, phases.size());
    assertTrue(phases.get(0) instanceof InitPhase);
    assertTrue(phases.get(1) instanceof JCardEngineInstallPhase);
    assertTrue(phases.get(2) instanceof TaskKindPhase);
    assertFalse(phases.get(1) instanceof InstallPhase);
  }

  /** Creates a tracking phase that records its execution. */
  private Phase trackingPhase(String name, List<String> executionLog) {
    return orchestrator -> {
      executionLog.add(name);
      // Simulate minimal phase behavior - set mock channel if this is "Init"
      if (name.equals("Init")) {
        orchestrator.setChannel(new MockApduChannel());
      }
    };
  }

  /**
   * Builds the same phase list as WriteCommand by delegating install-phase selection to the
   * production runtime registry.
   */
  private List<Phase> buildPhasesLikeCli(
      String runtime, boolean test, List<String> taskKinds, boolean skipInstall) {
    List<String> resolvedTaskKinds = resolveTaskKinds(test, taskKinds);
    boolean requireTasks = test || !taskKinds.isEmpty();

    List<Phase> phases = new ArrayList<>();
    phases.add(new InitPhase());
    if (!skipInstall) {
      Profile runtimeProfile = new Profile("cli-test", runtime, testProfile.secureChannel());
      phases.add(ApduChannelFactory.createInstallPhase(runtimeProfile));
    }
    for (String taskKind : resolvedTaskKinds) {
      phases.add(new TaskKindPhase(taskKind, requireTasks));
    }
    return phases;
  }

  private List<String> resolveTaskKinds(boolean test, List<String> taskKinds) {
    if (test) {
      return List.of("provision", "test");
    }
    if (!taskKinds.isEmpty()) {
      return taskKinds;
    }
    return List.of("provision");
  }

  /** Minimal mock APDU channel for testing. */
  private static class MockApduChannel implements ApduChannel {
    @Override
    public ResponseApdu transmit(CommandApdu capdu) {
      return new ResponseApdu(new byte[0], 0x90, 0x00); // Success response
    }

    @Override
    public void reset() {
      // No-op for testing
    }

    @Override
    public void close() {
      // No-op for testing
    }
  }
}
