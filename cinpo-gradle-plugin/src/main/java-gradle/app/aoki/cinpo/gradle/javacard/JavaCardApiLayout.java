package app.aoki.cinpo.gradle.javacard;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns the names and locations of the Java Card API artifacts used by the
 * Gradle plugin.
 *
 * <p>The applet compilation classpath and the CAP converter both read the API
 * jar and the extracted API export tree. Keeping the naming here prevents the
 * two paths from drifting apart when the target API version changes.</p>
 */
public final class JavaCardApiLayout {

    private static final String API_JAR_PREFIX = "api_classic-";
    private static final String API_EXPORT_PREFIX = "api_export_files_";
    private static final String JAR_SUFFIX = ".jar";

    private JavaCardApiLayout() {
    }

    /**
     * Returns the file name of the Java Card API jar for {@code targetApiVersion}.
     *
     * @param targetApiVersion manifest target API version, for example {@code 3.0.4}
     * @return file name such as {@code api_classic-3.0.4.jar}
     */
    public static String apiJarName(String targetApiVersion) {
        Objects.requireNonNull(targetApiVersion, "targetApiVersion");
        return API_JAR_PREFIX + targetApiVersion + JAR_SUFFIX;
    }

    /**
     * Returns the Java Card API jar below {@code toolLibraryDirectory}.
     *
     * @param toolLibraryDirectory directory holding {@code tools.jar} and the API jars
     * @param targetApiVersion manifest target API version
     * @return path to the API jar
     */
    public static Path apiJar(Path toolLibraryDirectory, String targetApiVersion) {
        Objects.requireNonNull(toolLibraryDirectory, "toolLibraryDirectory");
        return toolLibraryDirectory.resolve(apiJarName(targetApiVersion));
    }

    /**
     * Returns the directory name of the extracted API export tree for
     * {@code targetApiVersion}.
     *
     * @param targetApiVersion manifest target API version
     * @return directory name such as {@code api_export_files_3.0.4}
     */
    public static String apiExportsName(String targetApiVersion) {
        Objects.requireNonNull(targetApiVersion, "targetApiVersion");
        return API_EXPORT_PREFIX + targetApiVersion;
    }

    /**
     * Returns the API export directory below {@code apiExportRoot}.
     *
     * @param apiExportRoot root of the extracted API export trees
     * @param targetApiVersion manifest target API version
     * @return path to the API export directory
     */
    public static Path apiExports(Path apiExportRoot, String targetApiVersion) {
        Objects.requireNonNull(apiExportRoot, "apiExportRoot");
        return apiExportRoot.resolve(apiExportsName(targetApiVersion));
    }
}
