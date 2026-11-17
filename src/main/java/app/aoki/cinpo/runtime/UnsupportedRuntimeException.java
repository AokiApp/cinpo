package app.aoki.cinpo.runtime;

/**
 * Thrown when the requested runtime adapter is not bundled in the running appliance.
 *
 * <p>The appliance built by {@code cinpoJar} records which runtimes it bundles in the
 * {@value #RECORDED_RUNTIMES_ATTRIBUTE} manifest attribute. When an appliance is built
 * with narrower runtimes (for example, {@code pcsc} only), selecting an excluded runtime
 * fails here instead of with a {@code NoClassDefFoundError} deep inside an adapter.
 */
public final class UnsupportedRuntimeException extends RuntimeException {

    /** Manifest attribute naming the bundled runtimes, comma-separated. */
    public static final String RECORDED_RUNTIMES_ATTRIBUTE = "X-Cinpo-Runtimes";

    public UnsupportedRuntimeException(String message) {
        super(message);
    }

    public UnsupportedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
