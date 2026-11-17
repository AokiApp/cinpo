package app.aoki.cinpo.task;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a field to be injected by the Cinpo task loader.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
}
