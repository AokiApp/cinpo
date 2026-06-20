package app.aoki.cinpo.cli;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.ManifestLoader;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ProfileLoader;
import app.aoki.cinpo.orchestrator.Orchestrator;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.orchestrator.phase.InitPhase;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.orchestrator.phase.JCardEngineInstallPhase;
import app.aoki.cinpo.orchestrator.phase.TaskKindPhase;
import app.aoki.cinpo.task.TaskArguments;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Write command - install the applet and execute selected host-side task kinds.
 *
 * <p>Default workflow: init → install → provision.
 *
 * <p>Optional behaviors:
 * <ul>
 *   <li>{@code --test}: shorthand for provision then test</li>
 *   <li>{@code --task=<kind>}: execute explicit task kinds in CLI order</li>
 *   <li>{@code --skip-install}: skip installation and only run task kinds</li>
 *   <li>Arguments after {@code --}: forwarded verbatim to host-side tasks</li>
 * </ul>
 */
@Command(
    name = "write",
    description = "Install applet and execute selected task kinds"
)
public class WriteCommand implements Runnable {
 
    private static final Logger LOG = Logger.getLogger(WriteCommand.class.getName());
 
    @ParentCommand
    private CinpoCli rootCommand;

    @Option(
        names = {"--profile", "-p"},
        defaultValue = "jcdksim",
        description = "Profile name or YAML path (default: jcdksim builtin)"
    )
    private String profileName;

    @Option(
        names = "--task",
        description = "Run only the specified task kind; repeatable in execution order"
    )
    private List<String> taskKinds = new ArrayList<>();

    @Option(
        names = "--test",
        description = "Shortcut for --task=provision --task=test"
    )
    private boolean test;

    @Option(
        names = "--skip-install",
        description = "Skip applet installation and run only the selected task kinds"
    )
    private boolean skipInstall;

    @Override
    public void run() {
        try {
            LOG.fine(() -> "Loading profile: " + profileName);
            Profile profile = ProfileLoader.load(profileName);
 
            LOG.fine("Loading manifest from classpath");
            AppletManifest manifest = ManifestLoader.loadFromClasspath();
 
            List<Phase> phases = buildPhaseList(profile);
            LOG.fine(() -> "Starting orchestration with " + phases.size() + " phases");
 
            Orchestrator orchestrator = new Orchestrator(profile, manifest, phases, true, taskArguments());
            orchestrator.run();
 
            LOG.fine("Orchestration completed successfully");
        } catch (IOException e) {
            if (rootCommand != null && rootCommand.debugEnabled()) {
                LOG.severe("Write command failed due to I/O error: " + e.getMessage());
            } else {
                LOG.log(Level.SEVERE, "Write command failed due to I/O error: " + e.getMessage(), e);
            }
            throw new UncheckedIOException("Write command failed due to I/O error", e);
        } catch (RuntimeException e) {
            if (rootCommand != null && rootCommand.debugEnabled()) {
                LOG.severe("Write command failed: " + e.getMessage());
            } else {
                LOG.log(Level.SEVERE, "Write command failed: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private List<Phase> buildPhaseList(Profile profile) {
        List<String> resolvedTaskKinds = resolveTaskKinds();
        boolean requireTasks = hasExplicitTaskSelection();

        List<Phase> phases = new ArrayList<>();
        phases.add(new InitPhase());
        if (!skipInstall) {
            if ("jcardengine".equalsIgnoreCase(profile.runtime())) {
                phases.add(new JCardEngineInstallPhase());
            } else {
                phases.add(new InstallPhase());
            }
        }
        for (String taskKind : resolvedTaskKinds) {
            phases.add(new TaskKindPhase(taskKind, requireTasks));
        }
        return phases;
    }

    private List<String> resolveTaskKinds() {
        if (test) {
            if (!taskKinds.isEmpty()) {
                throw new IllegalArgumentException("--task and --test cannot be used together");
            }
            return List.of("provision", "test");
        }

        if (!taskKinds.isEmpty()) {
            return normalizeExplicitTaskKinds();
        }

        return List.of("provision");
    }

    private List<String> normalizeExplicitTaskKinds() {
        List<String> normalized = new ArrayList<>(taskKinds.size());
        Set<String> seen = new LinkedHashSet<>();

        for (String rawKind : taskKinds) {
            String kind = rawKind == null ? "" : rawKind.trim();
            if (kind.isEmpty()) {
                throw new IllegalArgumentException("--task requires a non-blank kind");
            }
            if (!seen.add(kind)) {
                throw new IllegalArgumentException("Duplicate --task kind: " + kind);
            }
            normalized.add(kind);
        }

        return List.copyOf(normalized);
    }

    private boolean hasExplicitTaskSelection() {
        return test || !taskKinds.isEmpty();
    }

    private TaskArguments taskArguments() {
        if (rootCommand == null) {
            return TaskArguments.empty();
        }
        return new TaskArguments(rootCommand.taskArguments());
    }
}
