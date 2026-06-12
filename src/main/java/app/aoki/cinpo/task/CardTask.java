package app.aoki.cinpo.task;

/**
 * A card-level task for an annotation-defined CINPO task kind.
 *
 * <p>Implementations declare their dependencies with {@code @Inject} fields and
 * are invoked with a no-argument {@link #run()} method. This keeps infrastructure
 * concerns in the framework and task intent in the task class.
 *
 * <p>Implementations must have a public no-arg constructor. Framework
 * instantiates the class via reflection, injects supported fields, and calls
 * {@code run} once.
 *
 * <p>Contract:
 * <ul>
 *   <li>Success: returns normally.</li>
 *   <li>Failure: throws an unchecked exception.</li>
 * </ul>
 */
@FunctionalInterface
public interface CardTask {

    /** Executes this card task. Required resources are supplied through {@code @Inject}. */
    void run();
}
