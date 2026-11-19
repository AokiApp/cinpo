package app.aoki.cinpo.gradle.javacard;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Files produced by one CAP build.
 *
 * <p>This type lives in {@code app.aoki.*} so it can cross the boundary between the
 * Gradle plugin classloader and the isolated Java Card tool classloader. The Oracle
 * classloader delegates {@code app.aoki.*} to the plugin classloader, so both sides
 * observe the same class.</p>
 *
 * @param generatedCapFile CAP written by the Oracle converter inside its own output
 *                          tree
 * @param stagedCapFile    copy of that CAP at the classpath resource path the
 *                         runtime installer reads
 */
public record CapBuildResult(Path generatedCapFile, Path stagedCapFile) {

    public CapBuildResult {
        Objects.requireNonNull(generatedCapFile, "generatedCapFile");
        Objects.requireNonNull(stagedCapFile, "stagedCapFile");
    }
}
