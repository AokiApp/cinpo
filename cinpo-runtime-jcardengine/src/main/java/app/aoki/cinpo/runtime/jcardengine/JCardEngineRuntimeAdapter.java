package app.aoki.cinpo.runtime.jcardengine;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.runtime.RuntimeAdapter;

/**
 * {@code runtime: jcardengine} — in-process Java Card runtime that installs applets
 * directly from their compiled classes.
 */
public final class JCardEngineRuntimeAdapter implements RuntimeAdapter {

    public static final String NAME = "jcardengine";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ApduChannel createChannel(Profile profile) {
        return new JCardEngineAdapter().createChannel();
    }

    @Override
    public Phase createInstallPhase(Profile profile) {
        return new JCardEngineInstallPhase();
    }
}
