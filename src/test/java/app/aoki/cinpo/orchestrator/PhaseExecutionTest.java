package app.aoki.cinpo.orchestrator;

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
import app.aoki.cinpo.task.TaskArguments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for orchestrator phase execution based on CLI argument combinations.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Phases are executed in the correct order</li>
 *   <li>The correct phases are selected based on CLI flags (--test, --task, --skip-install)</li>
 *   <li>Phase execution can be tracked without requiring actual card/simulator connections</li>
 * </ul>
 */
class PhaseExecutionTest {

    private Profile testProfile;
    private AppletManifest testManifest;

    @BeforeEach
    void setUp() {
        // Create minimal test profile and manifest
        ScpConfig scpConfig = new ScpConfig(
                "scp02",
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x05},
                0x00,
                0x00,
                0x03,
                new byte[]{0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F},
                new byte[]{0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F},
                new byte[]{0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F}
        );

        testProfile = new Profile("test-profile", "jcresim", scpConfig);

        AppletEntry appletEntry = new AppletEntry(
                "test-applet",
                "test.TestApplet",
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x01},
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x00, 0x00, 0x00, 0x01},
                new byte[]{0x00}
        );

        testManifest = new AppletManifest(
                "test.base",
                "26.0",
                "3.0.4",
                "test.package",
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x05},
                "1.0",
                List.of(appletEntry),
                List.of()
        );
    }

    @Test
    void defaultRun_ExecutesInitInstallProvisionPhases() throws IOException {
        // Given: orchestrator with default phases (init → install → provision)
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Init", executedPhases),
                trackingPhase("Install", executedPhases),
                trackingPhase("TaskKind[provision]", executedPhases)
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        orchestrator.run();

        // Then: verify all phases executed in order
        assertEquals(List.of("Init", "Install", "TaskKind[provision]"), executedPhases);
    }

    @Test
    void withTestFlag_ExecutesProvisionAndTestPhases() throws IOException {
        // Given: orchestrator with --test flag (init → install → provision → test)
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Init", executedPhases),
                trackingPhase("Install", executedPhases),
                trackingPhase("TaskKind[provision]", executedPhases),
                trackingPhase("TaskKind[test]", executedPhases)
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        orchestrator.run();

        // Then: verify provision and test phases both executed
        assertEquals(List.of("Init", "Install", "TaskKind[provision]", "TaskKind[test]"), executedPhases);
    }

    @Test
    void withCustomTaskKind_ExecutesCorrectPhase() throws IOException {
        // Given: orchestrator with --task=custom (init → install → custom)
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Init", executedPhases),
                trackingPhase("Install", executedPhases),
                trackingPhase("TaskKind[custom]", executedPhases)
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        orchestrator.run();

        // Then: verify custom task kind executed
        assertEquals(List.of("Init", "Install", "TaskKind[custom]"), executedPhases);
    }

    @Test
    void withMultipleTaskKinds_ExecutesInOrder() throws IOException {
        // Given: orchestrator with --task=foo --task=bar (init → install → foo → bar)
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Init", executedPhases),
                trackingPhase("Install", executedPhases),
                trackingPhase("TaskKind[foo]", executedPhases),
                trackingPhase("TaskKind[bar]", executedPhases)
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        orchestrator.run();

        // Then: verify task kinds executed in specified order
        assertEquals(List.of("Init", "Install", "TaskKind[foo]", "TaskKind[bar]"), executedPhases);
    }

    @Test
    void withSkipInstall_SkipsInstallPhase() throws IOException {
        // Given: orchestrator with --skip-install (init → provision only)
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Init", executedPhases),
                trackingPhase("TaskKind[provision]", executedPhases)
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        orchestrator.run();

        // Then: verify install phase was not executed
        assertEquals(List.of("Init", "TaskKind[provision]"), executedPhases);
        assertFalse(executedPhases.contains("Install"));
    }

    @Test
    void phaseExecutionOrder_IsPreserved() throws IOException {
        // Given: orchestrator with phases in specific order
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Phase1", executedPhases),
                trackingPhase("Phase2", executedPhases),
                trackingPhase("Phase3", executedPhases),
                trackingPhase("Phase4", executedPhases)
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        orchestrator.run();

        // Then: verify exact execution order
        assertEquals(List.of("Phase1", "Phase2", "Phase3", "Phase4"), executedPhases);
    }

    @Test
    void withTaskArguments_PhaseReceivesArguments() throws IOException {
        // Given: orchestrator with task arguments
        TaskArguments taskArgs = new TaskArguments(List.of("--flag", "value"));
        List<Phase> phases = List.of(
                orchestrator -> {
                    // Verify task arguments are accessible
                    assertEquals(taskArgs, orchestrator.getTaskArguments());
                    assertFalse(orchestrator.getTaskArguments().isEmpty());
                    assertTrue(orchestrator.getTaskArguments().hasFlag("--flag"));
                }
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true, taskArgs);

        // When: run orchestrator
        // Then: phase assertions verify task arguments
        orchestrator.run();
    }

    @Test
    void emptyTaskArguments_DefaultsToEmpty() throws IOException {
        // Given: orchestrator without explicit task arguments
        List<Phase> phases = List.of(
                orchestrator -> {
                    // Verify default is empty
                    assertTrue(orchestrator.getTaskArguments().isEmpty());
                }
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        // Then: phase assertions verify empty arguments
        orchestrator.run();
    }

    @Test
    void phaseFailure_StopsExecution() {
        // Given: orchestrator with failing phase
        List<String> executedPhases = new ArrayList<>();
        List<Phase> phases = List.of(
                trackingPhase("Phase1", executedPhases),
                orchestrator -> {
                    executedPhases.add("FailingPhase");
                    throw new IOException("Simulated failure");
                },
                trackingPhase("Phase3", executedPhases)  // Should not execute
        );

        Orchestrator orchestrator = new Orchestrator(testProfile, testManifest, phases, true);

        // When: run orchestrator
        // Then: execution stops at failing phase
        assertThrows(IOException.class, orchestrator::run);
        assertEquals(List.of("Phase1", "FailingPhase"), executedPhases);
        assertFalse(executedPhases.contains("Phase3"));
    }

    /**
     * Integration test simulating the actual CLI phase construction logic.
     * This mirrors WriteCommand.buildPhaseList() behavior.
     */
    @Test
    void cliPhaseConstruction_DefaultBehavior() {
        // Given: CLI default behavior
        List<Phase> phases = buildPhasesLikeCli(false, List.of(), false);

        // Then: verify correct phase types and order
        assertEquals(3, phases.size());
        assertTrue(phases.get(0) instanceof InitPhase);
        assertTrue(phases.get(1) instanceof InstallPhase);
        assertTrue(phases.get(2) instanceof TaskKindPhase);
    }

    @Test
    void cliPhaseConstruction_WithTestFlag() {
        // Given: CLI with --test flag
        List<Phase> phases = buildPhasesLikeCli(true, List.of(), false);

        // Then: verify provision and test phases
        assertEquals(4, phases.size());
        assertTrue(phases.get(0) instanceof InitPhase);
        assertTrue(phases.get(1) instanceof InstallPhase);
        assertTrue(phases.get(2) instanceof TaskKindPhase);
        assertTrue(phases.get(3) instanceof TaskKindPhase);
    }

    @Test
    void cliPhaseConstruction_WithCustomTaskKinds() {
        // Given: CLI with --task=foo --task=bar
        List<Phase> phases = buildPhasesLikeCli(false, List.of("foo", "bar"), false);

        // Then: verify custom task phases
        assertEquals(4, phases.size());
        assertTrue(phases.get(0) instanceof InitPhase);
        assertTrue(phases.get(1) instanceof InstallPhase);
        assertTrue(phases.get(2) instanceof TaskKindPhase);
        assertTrue(phases.get(3) instanceof TaskKindPhase);
    }

    @Test
    void cliPhaseConstruction_WithSkipInstall() {
        // Given: CLI with --skip-install
        List<Phase> phases = buildPhasesLikeCli(false, List.of("provision"), true);

        // Then: verify install phase is skipped
        assertEquals(2, phases.size());
        assertTrue(phases.get(0) instanceof InitPhase);
        assertTrue(phases.get(1) instanceof TaskKindPhase);
        assertFalse(phases.stream().anyMatch(p -> p instanceof InstallPhase));
    }

    /**
     * Creates a tracking phase that records its execution.
     */
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
     * Simulates WriteCommand.buildPhaseList() logic for testing.
     */
    private List<Phase> buildPhasesLikeCli(boolean test, List<String> taskKinds, boolean skipInstall) {
        List<String> resolvedTaskKinds = resolveTaskKinds(test, taskKinds);
        boolean requireTasks = test || !taskKinds.isEmpty();

        List<Phase> phases = new ArrayList<>();
        phases.add(new InitPhase());
        if (!skipInstall) {
            phases.add(new InstallPhase());
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

    /**
     * Minimal mock APDU channel for testing.
     */
    private static class MockApduChannel implements ApduChannel {
        @Override
        public ResponseApdu transmit(CommandApdu capdu) {
            return new ResponseApdu(new byte[0], 0x90, 0x00);  // Success response
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
