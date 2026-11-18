package app.aoki.cinpo.runtime;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.orchestrator.Phase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Factory for APDU channels, and the discovery registry for runtime adapters.
 *
 * <p>Runtimes register themselves via {@code META-INF/services/app.aoki.cinpo.runtime.RuntimeAdapter}.
 * A runtime whose classes or dependencies are absent from the classpath is silently unavailable,
 * not a discovery failure.
 */
public final class ApduChannelFactory {

    private ApduChannelFactory() {
    }

    /**
     * Returns every adapter discoverable on this classpath, in registration order.
     *
     * <p>Discovery runs on every call. One CLI run performs two or three lookups,
     * so a cached snapshot would only pin the first caller's context classloader
     * without buying measurable time.
     *
     * @return the registered adapters (never {@code null})
     */
    public static List<RuntimeAdapter> registered() {
        return loadAdapters();
    }

    /**
     * Returns the adapter for the given runtime identifier (case-insensitive).
     *
     * @param runtime runtime name from the profile, e.g. {@code jcresim}
     * @return the matching adapter
     * @throws IllegalArgumentException when no adapter is registered for {@code runtime}
     */
    public static RuntimeAdapter forName(String runtime) {
        Objects.requireNonNull(runtime, "Runtime name must not be null");
        List<RuntimeAdapter> adapters = registered();
        String name = runtime.trim();
        for (RuntimeAdapter adapter : adapters) {
            if (adapter.name().equalsIgnoreCase(name)) {
                return adapter;
            }
        }
        String available = adapters.stream()
                .map(RuntimeAdapter::name)
                .sorted()
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unsupported runtime: " + runtime + " (available: " + available + ")");
    }

    /**
     * Returns the adapter for the given profile's runtime identifier.
     *
     * @param profile the resolved profile
     * @return the matching adapter
     */
    public static RuntimeAdapter forProfile(Profile profile) {
        Objects.requireNonNull(profile, "Profile must not be null");
        return forName(profile.runtime());
    }

    /**
     * Creates the APDU channel for the given profile's runtime.
     *
     * @param profile the resolved profile
     * @return a new channel
     */
    public static ApduChannel create(Profile profile) {
        Objects.requireNonNull(profile, "Profile must not be null");
        return forProfile(profile).createChannel(profile);
    }

    /**
     * Creates the install phase for the given profile's runtime.
     *
     * @param profile the resolved profile
     * @return the install phase for the runtime
     */
    public static Phase createInstallPhase(Profile profile) {
        Objects.requireNonNull(profile, "Profile must not be null");
        return forProfile(profile).createInstallPhase(profile);
    }

    private static List<RuntimeAdapter> loadAdapters() {
        List<RuntimeAdapter> adapters = new ArrayList<>();
        ServiceLoader<RuntimeAdapter> loader = ServiceLoader.load(RuntimeAdapter.class);
        try {
            for (RuntimeAdapter adapter : loader) {
                adapters.add(adapter);
            }
        } catch (ServiceConfigurationError e) {
            // A malformed provider breaks the whole runtime selection; failing fast is
            // preferable to silently running with a partial registry.
            throw new IllegalStateException("Failed to discover runtime adapters", e);
        }
        return Collections.unmodifiableList(adapters);
    }
}
