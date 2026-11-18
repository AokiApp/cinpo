package app.aoki.cinpo.runtime;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Phase;

/**
 * SPI for a card runtime adapter.
 *
 * <p>A runtime adapter owns one transport and one install strategy. An implementation
 * declares its {@link #name()} (the value users put in the profile's {@code runtime:} key,
 * e.g. {@code jcresim}), creates the {@link ApduChannel} for that runtime, and
 * supplies the {@link Phase} used for installing applets.
 *
 * <p>Implementations are discovered via {@code META-INF/services/app.aoki.cinpo.runtime.RuntimeAdapter}.
 * A runtime whose module is absent from the appliance has no service entry and does not affect
 * the other runtimes. A service entry that names a provider which cannot be loaded makes discovery
 * fail as a whole.
 */
public interface RuntimeAdapter {

    /**
     * Returns the runtime identifier used by the profile, such as {@code jcresim}.
     *
     * @return the runtime name (case-insensitive)
     */
    String name();

    /**
     * Creates the APDU channel for this runtime.
     *
     * @param profile the resolved profile (the runtime value was already matched to this adapter)
     * @return a new channel
     */
    ApduChannel createChannel(Profile profile);

    /**
     * Creates the phase used to install applets for this runtime.
     *
     * @param profile the resolved profile
     * @return the install phase
     */
    Phase createInstallPhase(Profile profile);
}
