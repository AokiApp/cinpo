package app.aoki.cinpo.runtime.pcsc;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Phase;
import app.aoki.cinpo.orchestrator.phase.InstallPhase;
import app.aoki.cinpo.runtime.RuntimeAdapter;

/**
 * {@code runtime: pcsc} — PC/SC physical card reader. Applets are installed from CAP
 * artifacts over the GlobalPlatform secure channel.
 */
public final class PcscRuntimeAdapter implements RuntimeAdapter {

    public static final String NAME = "pcsc";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ApduChannel createChannel(Profile profile) {
        return new PcscAdapter(profile.pcsc()).createChannel();
    }

    @Override
    public Phase createInstallPhase(Profile profile) {
        return new InstallPhase();
    }
}
