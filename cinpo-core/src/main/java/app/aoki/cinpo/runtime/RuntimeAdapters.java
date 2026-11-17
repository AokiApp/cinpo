package app.aoki.cinpo.runtime;

import app.aoki.cinpo.config.Profile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Discovers and dispatches {@link RuntimeAdapter} implementations.
 *
 * <p>Runtimes register themselves via {@code META-INF/services/app.aoki.cinpo.runtime.RuntimeAdapter}.
 * A runtime whose classes or dependencies are absent from the classpath is silently
 * unavailable, not a discovery failure -- such a runtime must not make the rest unusable.
 */
public final class RuntimeAdapters {

    private static final List<RuntimeAdapter> ADAPTERS = loadAdapters();

    private RuntimeAdapters() {
    }

    /**
     * Returns every adapter discoverable on the current classpath, in registration order.
     *
     * @return the registered adapters (never {@code null})
     */
    public static List<RuntimeAdapter> registered() {
        return ADAPTERS;
    }

    /**
     * Returns the adapter for the given runtime identifier (case-insensitive).
     *
     * @param runtime runtime name from the profile, e.g. {@code jcresim}
     * @return the matching adapter
     * @throws IllegalArgumentException when no adapter is registered for {@code runtime}
     */
    public static RuntimeAdapter forName(String runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("Runtime name must not be null");
        }
        String normalized = runtime.trim().toLowerCase(Locale.ROOT);
        for (RuntimeAdapter adapter : ADAPTERS) {
            if (adapter.name().equalsIgnoreCase(normalized)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported runtime: " + runtime + " (available: " + availableRuntimeNames() + ")");
    }

    /**
     * Returns the adapter for the given profile's runtime identifier.
     *
     * @param profile the resolved profile
     * @return the matching adapter
     */
    public static RuntimeAdapter forProfile(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile must not be null");
        }
        return forName(profile.runtime());
    }

    private static String availableRuntimeNames() {
        List<String> names = new ArrayList<>(ADAPTERS.size());
        for (RuntimeAdapter adapter : ADAPTERS) {
            names.add(adapter.name());
        }
        Collections.sort(names);
        return String.join(", ", names);
    }

    private static List<RuntimeAdapter> loadAdapters() {
        List<RuntimeAdapter> adapters = new ArrayList<>();
        ServiceLoader<RuntimeAdapter> loader = ServiceLoader.load(RuntimeAdapter.class);
        try {
            for (RuntimeAdapter adapter : loader) {
                if (adapter != null) {
                    adapters.add(adapter);
                }
            }
        } catch (ServiceConfigurationError e) {
            // A malformed provider breaks the whole runtime selection; failing fast is
            // preferable to silently running with a partial registry.
            throw new IllegalStateException("Failed to discover runtime adapters", e);
        }
        return Collections.unmodifiableList(adapters);
    }
}
