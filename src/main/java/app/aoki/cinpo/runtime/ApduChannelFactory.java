package app.aoki.cinpo.runtime;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.runtime.jcardengine.JCardEngineAdapter;
import app.aoki.cinpo.runtime.jcresim.JcreSimAdapter;
import app.aoki.cinpo.runtime.pcsc.PcscAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.Manifest;

/**
 * Simple switch-based factory for creating APDU channel adapters.
 */
public final class ApduChannelFactory {

    private ApduChannelFactory() {
    }

    public static ApduChannel create(Profile profile) {
        Objects.requireNonNull(profile);
        String runtime = profile.runtime().toLowerCase(Locale.ROOT);
        requireBundledRuntime(runtime);
        return switch (runtime) {
            case "jcresim" -> new JcreSimAdapter().createChannel();
            case "jcardengine" -> new JCardEngineAdapter().createChannel();
            case "pcsc" -> new PcscAdapter(profile.pcsc()).createChannel();
            default -> throw new IllegalArgumentException("Unsupported runtime: " + profile.runtime());
        };
    }

    /**
     * Create an APDU channel for the specified runtime.
     *
     * @param runtime runtime identifier ("jcresim", "jcardengine", or "pcsc")
     * @return an APDU channel implementation
     * @throws IllegalArgumentException if the runtime is not supported
     * @throws UnsupportedRuntimeException if the runtime is not bundled in this appliance
     */
    public static ApduChannel create(String runtime) {
        Objects.requireNonNull(runtime);
        String normalized = runtime.toLowerCase(Locale.ROOT);
        requireBundledRuntime(normalized);
        return switch (normalized) {
            case "jcresim" -> new JcreSimAdapter().createChannel();
            case "jcardengine" -> new JCardEngineAdapter().createChannel();
            case "pcsc" -> new PcscAdapter().createChannel();
            default -> throw new IllegalArgumentException("Unsupported runtime: " + runtime);
        };
    }

    /**
     * Fails fast when an appliance was built without the requested runtime. Appliances
     * record their bundled runtimes in the {@code X-Cinpo-Runtimes} manifest attribute;
     * when the attribute is absent (for example, under {@code cinpoRun}), every runtime
     * is assumed to be bundled.
     */
    private static void requireBundledRuntime(String runtime) {
        List<String> bundled = bundledRuntimes();
        if (bundled == null || bundled.contains(runtime)) {
            return;
        }
        throw new UnsupportedRuntimeException(
                "runtime '" + runtime + "' is not bundled in this appliance (bundled: "
                        + String.join(",", bundled) + "). Rebuild cinpoJar with the runtime mentioned, e.g."
                        + " -Pcinpo.appliance.runtimes=...," + runtime);
    }

    private static List<String> bundledRuntimes() {
        return BundledRuntimeCache.VALUE;
    }

    // Initialized on first access, so cinpoRun keeps its current behavior.
    private static final class BundledRuntimeCache {
        private static final List<String> VALUE = readBundledRuntimes();
    }

    private static List<String> readBundledRuntimes() {
        try (InputStream in = ApduChannelFactory.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (in == null) {
                return null;
            }
            Manifest manifest = new Manifest(in);
            String value = manifest.getMainAttributes().getValue(UnsupportedRuntimeException.RECORDED_RUNTIMES_ATTRIBUTE);
            if (value == null || value.isBlank()) {
                return null;
            }
            List<String> parsed = List.of(value.split(",")).stream()
                    .map(token -> token.trim().toLowerCase(Locale.ROOT))
                    .filter(token -> !token.isEmpty())
                    .toList();
            return parsed.isEmpty() ? null : parsed;
        } catch (IOException e) {
            // If the appliance cannot be read, assume everything is bundled (pre-appliance behavior).
            return null;
        }
    }
}
