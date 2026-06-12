package app.aoki.cinpo.config;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Data model for applet manifest configuration.
 *
 * <p>
 * An {@code AppletManifest} defines the structure of a Java Card package
 * including:
 * <ul>
 * <li>Package metadata (name, load file AID, version)</li>
 * <li>Build configuration (SDK version, API version, base package)</li>
 * <li>List of applets within the package</li>
 * </ul>
 *
 * <p>
 * This manifest is the single source of truth for both:
 * <ul>
 * <li>CAP file generation</li>
 * <li>Installation parameters (instance AID, privileges)</li>
 * </ul>
 *
 * <h2>YAML Structure</h2>
 * <pre>
 * schema: cinpo.applet.v1
 * basePackage: app.aoki.mypackage
 * toolSdkVersion: "26.0"
 * targetApiVersion: "3.0.4"
 * package:
 *   name: app.aoki.mypackage
 *   aid: D392F000260100
 *   version: "1.0"
 * applets:
 *   - id: main
 *     className: app.aoki.mypackage.MainApplet
 *     classAid: D392F00026010001
 *     instanceAid: D392F000260100000001
 *     privilege: "00"
 * </pre>
 *
 * @param basePackage Base package for applet source files
 * @param toolSdkVersion Java Card SDK version for build tools
 * @param targetApiVersion Java Card API version target
 * @param packageName Java Card package name
 * @param loadFileAid Package AID (load file AID)
 * @param version Package version string
 * @param applets List of applet entries in this package
 * @see AppletEntry
 * @see ManifestLoader
 */
public record AppletManifest(
        String basePackage,
        String toolSdkVersion,
        String targetApiVersion,
        String packageName,
        byte[] loadFileAid,
        String version,
        List<AppletEntry> applets
        ) {

    private static final Set<String> SUPPORTED_TOOL_SDK_VERSIONS = Set.of("26.0");
    private static final Set<String> SUPPORTED_TARGET_API_VERSIONS = Set.of("3.0.4", "3.0.5", "3.1.0", "3.2.0");

    public AppletManifest {
        Objects.requireNonNull(basePackage);
        Objects.requireNonNull(toolSdkVersion);
        Objects.requireNonNull(targetApiVersion);
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(loadFileAid);
        Objects.requireNonNull(version);
        Objects.requireNonNull(applets);

        if (basePackage.isBlank()) {
            throw new IllegalArgumentException("basePackage must not be blank");
        }
        if (toolSdkVersion.isBlank()) {
            throw new IllegalArgumentException("toolSdkVersion must not be blank");
        }
        if (targetApiVersion.isBlank()) {
            throw new IllegalArgumentException("targetApiVersion must not be blank");
        }
        if (packageName.isBlank()) {
            throw new IllegalArgumentException("packageName must not be blank");
        }
        if (loadFileAid.length == 0) {
            throw new IllegalArgumentException("loadFileAid must not be empty");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (applets.isEmpty()) {
            throw new IllegalArgumentException("applets list must not be empty");
        }

        validateSdkCompatibility(toolSdkVersion, targetApiVersion);

        // Defensive copies
        loadFileAid = Arrays.copyOf(loadFileAid, loadFileAid.length);
        applets = List.copyOf(applets);
    }

    /**
     * Returns a defensive copy of the load file AID.
     */
    @Override
    public byte[] loadFileAid() {
        return Arrays.copyOf(loadFileAid, loadFileAid.length);
    }

    /**
     * Returns an immutable copy of the applets list.
     */
    @Override
    public List<AppletEntry> applets() {
        return applets;
    }

    private static void validateSdkCompatibility(String toolSdkVersion, String targetApiVersion) {
        if (!SUPPORTED_TOOL_SDK_VERSIONS.contains(toolSdkVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported toolSdkVersion: " + toolSdkVersion
                    + ". Supported values: " + SUPPORTED_TOOL_SDK_VERSIONS
            );
        }
        if (!SUPPORTED_TARGET_API_VERSIONS.contains(targetApiVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported targetApiVersion: " + targetApiVersion
                    + ". Supported values: " + SUPPORTED_TARGET_API_VERSIONS
            );
        }

        if (targetApiVersion.startsWith("3.2") && !toolSdkVersion.startsWith("24.")
                && !toolSdkVersion.startsWith("25.") && !toolSdkVersion.startsWith("26.")) {
            throw new IllegalArgumentException(
                    "targetApiVersion " + targetApiVersion
                    + " requires toolSdkVersion 24.0 or newer"
            );
        }
        if (targetApiVersion.startsWith("3.1") && !toolSdkVersion.startsWith("24.")
                && !toolSdkVersion.startsWith("25.") && !toolSdkVersion.startsWith("26.")) {
            throw new IllegalArgumentException(
                    "targetApiVersion " + targetApiVersion
                    + " requires toolSdkVersion 24.0 or newer"
            );
        }
        if (targetApiVersion.startsWith("3.0") && !toolSdkVersion.startsWith("24.")
                && !toolSdkVersion.startsWith("25.") && !toolSdkVersion.startsWith("26.")) {
            throw new IllegalArgumentException(
                    "targetApiVersion " + targetApiVersion
                    + " requires toolSdkVersion 24.0 or newer"
            );
        }
    }
}
