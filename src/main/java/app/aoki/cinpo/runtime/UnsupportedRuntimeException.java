package app.aoki.cinpo.runtime;

/**
 * Thrown when the requested runtime adapter is not bundled in the running appliance.
 *
 * <p>The appliance built by {@code cinpoJar} only contains what your project's
 * {@code runtimeClasspath} declares. If the jcardengine runtime is not declared,
 * selecting the jcardengine profile fails here with a message instead of a
 * {@code NoClassDefFoundError} deep inside an adapter.
 */
public final class UnsupportedRuntimeException extends RuntimeException {

    public UnsupportedRuntimeException(String message) {
        super(message);
    }

    public UnsupportedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
