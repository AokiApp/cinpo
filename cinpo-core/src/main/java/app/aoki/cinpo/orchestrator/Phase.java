package app.aoki.cinpo.orchestrator;

import java.io.IOException;

/**
 * Functional interface representing a single phase in the orchestration
 * workflow.
 *
 * <p>
 * A phase is a logical unit of work that operates on the shared
 * {@link Orchestrator} state. Phases are executed sequentially by the
 * orchestrator.
 *
 * <h2>Built-in Phases</h2>
 * <ul>
 * <li>{@link app.aoki.cinpo.orchestrator.phase.InitPhase} - Opens the APDU
 * channel</li>
 * <li>{@link app.aoki.cinpo.orchestrator.phase.InstallPhase} - Installs CAP
 * files</li>
 * <li>{@link app.aoki.cinpo.orchestrator.phase.TaskKindPhase} - Runs any
 * annotation-defined task kind</li>
 * </ul>
 *
 * <h2>Custom Phases</h2>
 * Custom phases can be implemented as lambda expressions or classes:
 * <pre>{@code
 * Phase customPhase = orchestrator -> {
 *     // Access orchestrator state
 *     ApduChannel channel = orchestrator.requireChannel();
 *     // ... custom logic
 * };
 * }</pre>
 *
 * @see Orchestrator
 */
@FunctionalInterface
public interface Phase {

    /**
     * Executes this phase using the provided orchestrator context.
     *
     * @param orchestrator the orchestrator providing shared state and
     * dependencies
     * @throws IOException if the phase fails due to I/O errors
     * @throws RuntimeException if the phase fails due to protocol or validation
     * errors
     */
    void execute(Orchestrator orchestrator) throws IOException;
}
