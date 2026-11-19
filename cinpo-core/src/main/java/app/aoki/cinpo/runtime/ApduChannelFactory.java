package app.aoki.cinpo.runtime;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Factory for APDU channels and install phases, resolved through the runtime SPI.
 *
 * <p>Runtimes register themselves via {@code META-INF/services/app.aoki.cinpo.runtime.RuntimeAdapter}.
 * A runtime whose module is absent from the classpath is simply not discovered; a service
 * entry that cannot be loaded fails discovery fast with an {@link IllegalStateException}.
 */
public final class ApduChannelFactory {

    private ApduChannelFactory() {
    }

    /**
     * Creates the APDU channel for the given profile's runtime.
     *
     * @param profile the resolved profile
     * @return a new channel
     * @throws IllegalArgumentException when no adapter is registered for the runtime
     */
    public static ApduChannel create(Profile profile) {
        return adapterFor(profile).createChannel(profile);
    }

    /**
     * Creates the install phase for the given profile's runtime.
     *
     * @param profile the resolved profile
     * @return the install phase for the runtime
     * @throws IllegalArgumentException when no adapter is registered for the runtime
     */
    public static Phase createInstallPhase(Profile profile) {
        return adapterFor(profile).createInstallPhase(profile);
    }

    /**
     * Resolves the profile's runtime name to a registered adapter.
     *
     * @param profile the resolved profile
     * @return the matching adapter
     * @throws IllegalArgumentException when no adapter is registered for the runtime
     */
    private static RuntimeAdapter adapterFor(Profile profile) {
        Objects.requireNonNull(profile, "Profile must not be null");
        String name = profile.runtime().trim();
        List<String> available = new ArrayList<>();
        try {
            for (RuntimeAdapter adapter : ServiceLoader.load(RuntimeAdapter.class)) {
                String adapterName = adapter.name();
                available.add(adapterName);
                if (adapterName.equalsIgnoreCase(name)) {
                    return adapter;
                }
            }
        } catch (ServiceConfigurationError e) {
            // A malformed provider breaks the whole runtime selection; failing fast is
            // preferable to silently running with a partial registry.
            throw new IllegalStateException("Failed to discover runtime adapters", e);
        }
        String availableList = available.isEmpty() ? "none on the classpath" : String.join(", ", available);
        throw new IllegalArgumentException("Unsupported runtime: " + profile.runtime()
                + " (available: " + availableList + ")."
                + " Add the matching app.aoki.cinpo:cinpo-runtime-* dependency to your build.");
    }
}
