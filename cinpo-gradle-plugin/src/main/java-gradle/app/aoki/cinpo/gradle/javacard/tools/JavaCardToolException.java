package app.aoki.cinpo.gradle.javacard.tools;

/**
 * Unchecked exception for Java Card tool integration failures.
 *
 * <p>These failures are build configuration or Oracle tool execution problems,
 * so surfacing them as unchecked exceptions keeps Gradle task code compact while
 * preserving the original cause.</p>
 */
public final class JavaCardToolException extends RuntimeException {
    public JavaCardToolException(String message) {
        super(message);
    }

    public JavaCardToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
