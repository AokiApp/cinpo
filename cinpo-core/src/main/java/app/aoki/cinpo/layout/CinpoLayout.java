package app.aoki.cinpo.layout;

/**
 * Defines the classpath resource layout shared by the Gradle plugin and the runtimes.
 *
 * <p>Two contracts cross module boundaries and must stay identical everywhere:</p>
 * <ul>
 *   <li>the install phase loads a generated CAP file as {@code cap/<packageName>.cap}</li>
 *   <li>the jcresim adapter looks up its simulator runtime below
 *       {@code cinpo-simulator/<platform>/}</li>
 * </ul>
 *
 * <p>Owning both rules here keeps the producer (the Gradle plugin) and the consumer (the
 * runtimes) from drifting apart through duplicated string literals.</p>
 */
public final class CinpoLayout {

    /** Classpath directory that holds generated CAP resources. */
    public static final String CAP_DIRECTORY = "cap";

    /** Classpath directory that holds the bundled simulator runtime. */
    public static final String SIMULATOR_DIRECTORY = "cinpo-simulator";

    private CinpoLayout() {
    }

    /**
     * Builds the classpath resource path of a generated CAP file.
     *
     * @param packageName manifest package name, used verbatim with its dots
     * @return the resource path {@code cap/<packageName>.cap}
     */
    public static String capResourcePath(String packageName) {
        return CAP_DIRECTORY + "/" + packageName + ".cap";
    }

    /**
     * Builds the classpath resource prefix of one platform's simulator runtime.
     *
     * @param platform platform segment such as {@code linux-amd64}
     * @return the resource prefix {@code cinpo-simulator/<platform>/}, including the
     *         trailing slash
     */
    public static String simulatorResourcePrefix(String platform) {
        return SIMULATOR_DIRECTORY + "/" + platform + "/";
    }
}
