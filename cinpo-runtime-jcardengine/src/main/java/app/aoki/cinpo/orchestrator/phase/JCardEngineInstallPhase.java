package app.aoki.cinpo.orchestrator.phase;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.orchestrator.Orchestrator;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.runtime.jcardengine.JCardEngineAdapter;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Phase that installs applets into a fresh JCardEngine runtime by class name.
 */
public final class JCardEngineInstallPhase implements Phase {

    private static final Logger LOG = Logger.getLogger(JCardEngineInstallPhase.class.getName());

    public JCardEngineInstallPhase() {
    }

    @Override
    public void execute(Orchestrator orchestrator) throws IOException {
        ApduChannel channel = orchestrator.requireChannel();
        if (!(channel instanceof JCardEngineAdapter.JCardEngineChannel jcardengineChannel)) {
            throw new IllegalStateException(
                    "JCardEngineInstallPhase requires a JCardEngine APDU channel but got: "
                            + channel.getClass().getName());
        }

        AppletManifest manifest = orchestrator.getManifest();
        LOG.fine(() -> "Installing " + manifest.applets().size() + " applets into fresh JCardEngine runtime");
        jcardengineChannel.install(manifest);
    }
}
