package app.aoki.cinpo.runtime;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import java.util.Objects;

/**
 * Factory for creating APDU channel adapters.
 *
 * <p>Delegates to the {@link RuntimeAdapter} discovered for the requested runtime.
 */
public final class ApduChannelFactory {

    private ApduChannelFactory() {
    }

    public static ApduChannel create(Profile profile) {
        Objects.requireNonNull(profile);
        return RuntimeAdapters.forProfile(profile).createChannel(profile);
    }
}
