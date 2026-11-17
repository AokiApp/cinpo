package app.aoki.cinpo.orchestrator;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.task.TaskArguments;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Main orchestration engine for the CINPO framework.
 *
 * <p>Orchestrator manages the execution lifecycle of a card operation workflow:
 * <ol>
 *   <li>Initialize APDU channel connection to the card/simulator</li>
 *   <li>Install CAP files via GlobalPlatform</li>
 *   <li>Execute selected task kinds via task phases such as {@link app.aoki.cinpo.orchestrator.phase.TaskKindPhase}</li>
 * </ol>
 *
 * <p>The orchestration sequence is defined by a list of {@link Phase} objects passed
 * to the constructor. Each phase is executed in order, and has access to the shared
 * orchestrator state (profile, manifest, channel, task arguments).
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Profile profile = ProfileLoader.load("jcdksim");
 * AppletManifest manifest = ManifestLoader.load();
 * List<Phase> phases = List.of(
 *     new InitPhase(),
 *     new InstallPhase(),
 *     new TaskKindPhase("provision", false),
 *     new TaskKindPhase("test", true)
 * );
 * Orchestrator orchestrator = new Orchestrator(profile, manifest, phases, true, TaskArguments.empty());
 * orchestrator.run();
 * }</pre>
 *
 * <h2>Resource Management</h2>
 * The orchestrator automatically closes the APDU channel when {@link #run()} completes,
 * whether the run succeeds or fails. Callers do not need to manage channel lifecycle.
 *
 * @see Phase
 * @see app.aoki.cinpo.orchestrator.phase.InitPhase
 * @see app.aoki.cinpo.orchestrator.phase.InstallPhase
 * @see app.aoki.cinpo.orchestrator.phase.TaskKindPhase
 */
public final class Orchestrator {
    private static final Logger LOG = Logger.getLogger(Orchestrator.class.getName());

    private final Profile profile;
    private final AppletManifest manifest;
    private final List<Phase> phases;
    private final boolean deleteFirst;
    private final TaskArguments taskArguments;

    private ApduChannel channel;  // Mutable state set by InitPhase

    /**
     * Creates a new orchestrator with the specified configuration.
     *
     * @param profile      runtime profile (defines which card/simulator to use)
     * @param manifest     applet manifest (defines what to install)
     * @param phases       ordered list of phases to execute
     * @param deleteFirst  whether to delete existing applets before installation
     * @throws NullPointerException if any parameter is null
     */
    public Orchestrator(Profile profile, AppletManifest manifest, List<Phase> phases, boolean deleteFirst) {
        this(profile, manifest, phases, deleteFirst, TaskArguments.empty());
    }

    public Orchestrator(Profile profile, AppletManifest manifest, List<Phase> phases, boolean deleteFirst,
            TaskArguments taskArguments) {
        this.profile = Objects.requireNonNull(profile);
        this.manifest = Objects.requireNonNull(manifest);
        this.phases = List.copyOf(phases);
        this.deleteFirst = deleteFirst;
        this.taskArguments = Objects.requireNonNull(taskArguments);
    }

    /**
     * Executes all phases in order.
     *
     * <p>The APDU channel is automatically closed when this method completes,
     * whether the run succeeds or fails.
     *
     * @throws IOException if any phase fails due to I/O errors
     * @throws RuntimeException if any phase fails due to protocol or validation errors
     */
    public void run() throws IOException {
        LOG.fine(() -> "Starting orchestration with " + phases.size() + " phases");
        try {
            for (Phase phase : phases) {
                String phaseName = phase.getClass().getSimpleName();
                LOG.fine(() -> "Executing phase: " + phaseName);
                phase.execute(this);
                LOG.fine(() -> "Completed phase: " + phaseName);
            }
        } finally {
            if (channel != null) {
                LOG.fine("Closing APDU channel");
                channel.close();
            }
            LOG.fine("Orchestration finished");
        }
    }

    /**
     * Returns the runtime profile.
     *
     * @return the profile passed to the constructor
     */
    public Profile getProfile() {
        return profile;
    }

    /**
     * Returns the applet manifest.
     *
     * @return the manifest passed to the constructor
     */
    public AppletManifest getManifest() {
        return manifest;
    }

    /**
     * Returns the current APDU channel, or null if not yet initialized.
     *
     * @return the channel, or null
     */
    public ApduChannel getChannel() {
        return channel;
    }

    /**
     * Sets the APDU channel.
     *
     * <p>This method is called by {@link app.aoki.cinpo.orchestrator.phase.InitPhase}
     * to store the channel after opening it.
     *
     * @param channel the newly opened channel
     */
    public void setChannel(ApduChannel channel) {
        this.channel = channel;
    }

    /**
     * Returns the APDU channel, throwing an exception if not yet initialized.
     *
     * @return the channel
     * @throws IllegalStateException if the channel has not been initialized
     */
    public ApduChannel requireChannel() {
        if (channel == null) {
            throw new IllegalStateException("APDU channel not initialized. Ensure InitPhase has executed.");
        }
        return channel;
    }

    /**
     * Returns whether existing applets should be deleted before installation.
     *
     * @return true if delete-first mode is enabled
     */
    public boolean isDeleteFirst() {
        return deleteFirst;
    }

    public TaskArguments getTaskArguments() {
        return taskArguments;
    }
}
