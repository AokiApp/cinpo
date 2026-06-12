package app.aoki.cinpo.task;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Declares a host-side Cinpo task kind for build-time indexing and runtime discovery.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface CardTaskDef {
    /** Task kind such as {@code "provision"} or {@code "test"}. */
    String value();

    /** Execution order within the same kind. Ties are resolved by FQCN. */
    int order() default 0;
}
