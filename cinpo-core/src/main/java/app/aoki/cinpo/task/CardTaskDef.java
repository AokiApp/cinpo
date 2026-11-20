package app.aoki.cinpo.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a host-side Cinpo task kind for build-time validation and indexing, and for runtime discovery.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CardTaskDef {
    /** Task kind such as {@code "provision"} or {@code "test"}. */
    String value();

    /** Execution order within the same kind. Ties are resolved by FQCN. */
    int order() default 0;
}
