package app.aoki.cinpo.runtime.jcresim;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.runtime.RuntimeAdapter;

/**
 * {@code runtime: jcresim} — Oracle JCDK simulator. Applets are installed from CAP
 * artifacts over the GlobalPlatform secure channel.
 */
public final class JcreSimRuntimeAdapter implements RuntimeAdapter {

    public static final String NAME = "jcresim";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ApduChannel createChannel(Profile profile) {
        return new JcreSimAdapter().createChannel();
    }

    @Override
    public Phase createInstallPhase(Profile profile) {
        return new InstallPhase();
    }
}
