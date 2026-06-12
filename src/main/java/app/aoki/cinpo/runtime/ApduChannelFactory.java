package app.aoki.cinpo.runtime;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.runtime.jcresim.JcreSimAdapter;
import app.aoki.cinpo.runtime.pcsc.PcscAdapter;
import java.util.Objects;

/**
 * Simple switch-based factory for creating APDU channel adapters.
 */
public final class ApduChannelFactory {

    private ApduChannelFactory() {
    }

    public static ApduChannel create(Profile profile) {
        Objects.requireNonNull(profile);
        return switch (profile.runtime().toLowerCase()) {
            case "jcresim" -> new JcreSimAdapter().createChannel();
            case "pcsc" -> new PcscAdapter(profile.pcsc()).createChannel();
            default -> throw new IllegalArgumentException("Unsupported runtime: " + profile.runtime());
        };
    }

    /**
     * Create an APDU channel for the specified runtime.
     *
     * @param runtime runtime identifier ("jcresim" or "pcsc")
     * @return an APDU channel implementation
     * @throws IllegalArgumentException if the runtime is not supported
     */
    public static ApduChannel create(String runtime) {
        Objects.requireNonNull(runtime);

        return switch (runtime.toLowerCase()) {
            case "jcresim" -> new JcreSimAdapter().createChannel();
            case "pcsc" -> new PcscAdapter().createChannel();
            default -> throw new IllegalArgumentException("Unsupported runtime: " + runtime);
        };
    }
}
