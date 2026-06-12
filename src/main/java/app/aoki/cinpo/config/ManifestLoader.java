package app.aoki.cinpo.config;

import app.aoki.cinpo.util.Util;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads {@link AppletManifest} configurations from YAML files.
 *
 * <h2>Loading Strategies</h2>
 * <ul>
 *   <li>{@link #load(Path)} - Load from filesystem path</li>
 *   <li>{@link #loadFromClasspath()} - Load from classpath resource {@code manifest.yaml}</li>
 * </ul>
 *
 * <h2>YAML Schema</h2>
 * <pre>
 * schema: cinpo.applet.v1
 * basePackage: app.aoki.cinpo.example
 * toolSdkVersion: "26.0"
 * targetApiVersion: "3.0.4"
 * package:
 *   name: app.aoki.cinpo.example
 *   aid: D392F000260100
 *   version: "1.0"
 * applets:
 *   - id: main
 *     className: app.aoki.cinpo.example.MainApplet
 *     classAid: D392F00026010001
 *     instanceAid: D392F000260100000001
 *     privilege: "00"
 * </pre>
 *
 * @see AppletManifest
 * @see AppletEntry
 */
public final class ManifestLoader {
 
    private static final Logger LOG = Logger.getLogger(ManifestLoader.class.getName());
    private static final String EXPECTED_SCHEMA = "cinpo.applet.v1";
    private static final String LEGACY_SCHEMA = "cardemus.applet.v1";
    private static final String CLASSPATH_RESOURCE = "manifest.yaml";

    private ManifestLoader() {
        // Utility class - no instantiation
    }

    /**
     * Loads a manifest from a filesystem path.
     *
     * @param path path to manifest YAML file
     * @return loaded {@link AppletManifest}
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if YAML content is invalid
     */
    public static AppletManifest load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Manifest file not found: " + path.toAbsolutePath());
        }

        String yamlContent = Files.readString(path);
        return parse(yamlContent, path.toString());
    }

    /**
     * Loads a manifest from the classpath resource {@code manifest.yaml}.
     *
     * <p>This method is typically used in production mode when the manifest is
     * embedded in the fat JAR.
     *
     * @return loaded {@link AppletManifest}
     * @throws IOException if resource cannot be read
     * @throws IllegalArgumentException if YAML content is invalid
     */
    public static AppletManifest loadFromClasspath() throws IOException {
        InputStream stream = ManifestLoader.class.getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE);
        if (stream == null) {
            throw new IOException("Manifest resource not found on classpath: " + CLASSPATH_RESOURCE);
        }

        try (stream) {
            String yamlContent = new String(stream.readAllBytes());
            return parse(yamlContent, "classpath:" + CLASSPATH_RESOURCE);
        }
    }

    /**
     * Parses a manifest from YAML content.
     *
     * @param yamlContent YAML content string
     * @param source source description (for error messages)
     * @return parsed {@link AppletManifest}
     * @throws IllegalArgumentException if YAML content is invalid
     */
    private static AppletManifest parse(String yamlContent, String source) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(yamlContent);

        if (data == null) {
            throw new IllegalArgumentException("Manifest YAML is empty: " + source);
        }

        // Validate schema version
        String schema = YamlMapUtil.getString(data, "schema", source);
        if (!EXPECTED_SCHEMA.equals(schema)) {
            if (LEGACY_SCHEMA.equals(schema)) {
                LOG.warning(() -> "Legacy manifest schema in " + source
                        + ": " + LEGACY_SCHEMA + " is accepted for now; prefer " + EXPECTED_SCHEMA);
            } else {
                LOG.warning(() -> "Manifest schema mismatch: " + schema
                        + " (expected: " + EXPECTED_SCHEMA + ") in " + source);
            }
        }

        // Parse top-level fields
        String basePackage = YamlMapUtil.getString(data, "basePackage", source);
        String toolSdkVersion = YamlMapUtil.getString(data, "toolSdkVersion", source);
        String targetApiVersion = YamlMapUtil.getString(data, "targetApiVersion", source);

        // Parse package block
        Map<String, Object> packageData = YamlMapUtil.getMap(data, "package", source);
        String packageName = YamlMapUtil.getString(packageData, "name", source + ".package");
        String aidHex = YamlMapUtil.getString(packageData, "aid", source + ".package");
        String version = YamlMapUtil.getString(packageData, "version", source + ".package");

        byte[] loadFileAid;
        try {
            loadFileAid = Util.parseHex(aidHex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid package.aid hex in " + source + ": " + e.getMessage(), e);
        }

        // Parse applets block
        List<Map<String, Object>> appletsData = YamlMapUtil.getMapList(data, "applets", source);
        List<AppletEntry> applets = new ArrayList<>();

        for (int i = 0; i < appletsData.size(); i++) {
            Map<String, Object> appletData = appletsData.get(i);
            applets.add(parseAppletEntry(appletData, source, i));
        }

        return new AppletManifest(
                basePackage,
                toolSdkVersion,
                targetApiVersion,
                packageName,
                loadFileAid,
                version,
                applets
        );
    }

    /**
     * Parses a single applet entry from YAML.
     *
     * @param data applet data map
     * @param source source description (for error messages)
     * @param index applet index in list (for error messages)
     * @return parsed {@link AppletEntry}
     */
    private static AppletEntry parseAppletEntry(Map<String, Object> data, String source, int index) {
        String context = source + ".applets[" + index + "]";

        String id = YamlMapUtil.getString(data, "id", context);
        String className = YamlMapUtil.getString(data, "className", context);
        String classAidHex = YamlMapUtil.getString(data, "classAid", context);
        String instanceAidHex = YamlMapUtil.getString(data, "instanceAid", context);
        String privilegeHex = YamlMapUtil.getString(data, "privilege", context);

        try {
            byte[] classAid = Util.parseHex(classAidHex);
            byte[] instanceAid = Util.parseHex(instanceAidHex);
            byte[] privileges = Util.parseHex(privilegeHex);

            return new AppletEntry(id, className, classAid, instanceAid, privileges);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid hex string in " + context + ": " + e.getMessage(),
                    e
            );
        }
    }

}
