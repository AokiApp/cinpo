package app.aoki.cinpo.orchestrator.phase;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Orchestrator;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskLoader;
import app.aoki.cinpo.task.TaskContext;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Phase that executes all tasks for a configured task kind.
 *
 * <p>The task kind is resolved by the CLI layer and loaded via {@link CardTaskLoader}
 * from {@code META-INF/cinpo/<kind>}.
 */
public final class TaskKindPhase implements Phase {

    private static final Logger LOG = Logger.getLogger(TaskKindPhase.class.getName());
 
    private final String kind;
    private final boolean requireTasks;

    public TaskKindPhase(String kind, boolean requireTasks) {
        this.kind = Objects.requireNonNull(kind);
        this.requireTasks = requireTasks;
    }

    @Override
    public void execute(Orchestrator orchestrator) throws IOException {
        ApduChannel channel = orchestrator.requireChannel();
        AppletManifest manifest = orchestrator.getManifest();
        Profile profile = orchestrator.getProfile();

        TaskContext ctx = new TaskContext(channel, manifest, profile, orchestrator.getTaskArguments());
        List<CardTask> tasks = CardTaskLoader.loadAll(kind, ctx);
        LOG.fine(() -> "Loaded " + tasks.size() + " tasks for kind: " + kind);
        if (tasks.isEmpty() && requireTasks) {
            throw new IllegalStateException("No CINPO tasks found for kind: " + kind);
        }
 
        for (CardTask task : tasks) {
            String taskName = task.getClass().getName();
            LOG.fine(() -> "Running task: " + taskName);
            task.run();
            LOG.fine(() -> "Completed task: " + taskName);
        }
    }
}
