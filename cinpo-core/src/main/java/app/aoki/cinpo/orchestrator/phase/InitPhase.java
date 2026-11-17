package app.aoki.cinpo.orchestrator.phase;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Orchestrator;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.runtime.ApduChannelFactory;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Phase that initializes the APDU channel connection to the card runtime.
 *
 * <p>This phase:
 * <ol>
 *   <li>Reads the runtime identifier from the profile (e.g., "jcresim", "jcardengine", "pcsc")</li>
 *   <li>Creates an appropriate {@link ApduChannel} via {@link ApduChannelFactory}</li>
 *   <li>Stores the channel in the orchestrator for use by subsequent phases</li>
 * </ol>
 *
 * <h2>Runtime Support</h2>
 * Supported runtime identifiers:
 * <ul>
 *   <li>{@code "jcresim"} - Oracle JCDK simulator runtime</li>
 *   <li>{@code "jcardengine"} - class-installed in-process Java Card runtime</li>
 *   <li>{@code "pcsc"} - PC/SC physical card reader</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * This phase should typically be the first phase in the orchestration sequence.
 * The APDU channel remains open until the orchestrator's run method completes,
 * at which point it is automatically closed.
 *
 * @see ApduChannelFactory
 * @see ApduChannel
 */
public final class InitPhase implements Phase {

    private static final Logger LOG = Logger.getLogger(InitPhase.class.getName());
 
    /**
     * Creates a new InitPhase instance.
     */
    public InitPhase() {
    }

    @Override
    public void execute(Orchestrator orchestrator) throws IOException {
        Profile profile = orchestrator.getProfile();
        LOG.fine(() -> "Opening APDU channel for runtime: " + profile.runtime());
        ApduChannel channel = ApduChannelFactory.create(profile);
        orchestrator.setChannel(channel);
        LOG.fine("APDU channel initialized");
    }
}
