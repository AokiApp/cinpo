package app.aoki.cinpo.config;

import app.aoki.cinpo.util.Util;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link Profile} configurations from YAML files or builtin defaults.
 *
 * <h2>Profile Resolution Order</h2>
 * <ol>
 *   <li>If {@code nameOrPath} is an absolute path, load from that file</li>
 *   <li>If {@code nameOrPath} contains "/" or ends with ".yaml", load that path relative to the project directory</li>
 *   <li>Otherwise, check {@code profile/{nameOrPath}.yaml} below the project directory</li>
 *   <li>If file not found and name is "jcdksim", return {@link BuiltinProfiles#JCDKSIM}</li>
 *   <li>If not found, throw {@link IOException} with the searched location</li>
 * </ol>
 *
 * <h2>YAML Schema</h2>
 * <pre>
 * schema: cinpo.profile.v1
 * name: jcdksim
 * runtime: jcresim
 * pcsc:
 *   reader: My Smart Card Reader
 *   excludedReaders:
 *     - Built-in Contactless Reader
 * secureChannel:
 *   protocol: scp03
 *   isdAid: A000000151000000
 *   keyVersionNumber: 16
 *   keyIdentifier: 0
 *   securityLevel: 1
 *   encKey: 1111111111111111111111111111111111111111111111111111111111111111
 *   macKey: 2222222222222222222222222222222222222222222222222222222222222222
 *   dekKey: 3333333333333333333333333333333333333333333333333333333333333333
 * </pre>
 *
 * @see Profile
 * @see BuiltinProfiles
 */
public final class ProfileLoader {

    private static final String EXPECTED_SCHEMA = "cinpo.profile.v1";
    private static final String PROFILE_DIR = "profile";

    private ProfileLoader() {
        // Utility class - no instantiation
    }

    /**
     * Loads a profile by name or file path.
     *
     * @param nameOrPath profile name (e.g., "jcdksim", "card1") or file path
     * @return loaded {@link Profile}
     * @throws IOException if profile cannot be loaded or parsed
     * @throws IllegalArgumentException if YAML content is invalid
     */
    public static Profile load(String nameOrPath) throws IOException {
        return load(nameOrPath, Paths.get("."));
    }

    /**
     * Loads a profile by name or file path using an explicit project directory.
     *
     * @param nameOrPath profile name (e.g., "jcdksim", "card1") or file path
     * @param projectDirectory application project directory used for relative profile lookup
     * @return loaded {@link Profile}
     * @throws IOException if profile cannot be loaded or parsed
     * @throws IllegalArgumentException if YAML content is invalid
     */
    public static Profile load(String nameOrPath, Path projectDirectory) throws IOException {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            throw new IllegalArgumentException("Profile name or path must not be null or blank");
        }
        if (projectDirectory == null) {
            throw new NullPointerException("projectDirectory");
        }

        List<Path> candidates = resolveCandidatePaths(nameOrPath, projectDirectory);
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return loadFromFile(candidate);
            }
        }

        if ("jcdksim".equals(nameOrPath)) {
            return BuiltinProfiles.JCDKSIM;
        }

        throw new IOException(
                "Profile not found: " + nameOrPath +
                        "\nSearched:\n  - " + candidates.stream()
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .reduce((left, right) -> left + "\n  - " + right)
                        .orElse("(no path)") +
                        "\nAvailable builtins: jcdksim"
        );
    }

    /**
     * Resolves a profile name or path to every file path that should be checked.
     *
     * @param nameOrPath profile name or path
     * @param projectDirectory application project directory used for relative lookup
     * @return ordered candidate paths
     */
    private static List<Path> resolveCandidatePaths(String nameOrPath, Path projectDirectory) {
        Path normalizedProjectDirectory = projectDirectory.toAbsolutePath().normalize();
        Path suppliedPath = Paths.get(nameOrPath);
        if (suppliedPath.isAbsolute()) {
            return List.of(suppliedPath.normalize());
        }

        if (isPathLike(nameOrPath)) {
            return List.of(normalizedProjectDirectory.resolve(suppliedPath).normalize());
        }

        return List.of(normalizedProjectDirectory.resolve(PROFILE_DIR).resolve(nameOrPath + ".yaml").normalize());
    }

    private static boolean isPathLike(String nameOrPath) {
        return nameOrPath.contains("/") || nameOrPath.contains("\\") || nameOrPath.endsWith(".yaml") || nameOrPath.endsWith(".yml");
    }

    /**
     * Loads a profile from a YAML file.
     *
     * @param path path to YAML file
     * @return loaded {@link Profile}
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if YAML content is invalid
     */
    private static Profile loadFromFile(Path path) throws IOException {
        String yamlContent = Files.readString(path);
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(yamlContent);

        if (data == null) {
            throw new IllegalArgumentException("Profile YAML is empty: " + path);
        }

        // Validate schema version
        String schema = YamlMapUtil.getString(data, "schema", path);
        if (!EXPECTED_SCHEMA.equals(schema)) {
            throw new IllegalArgumentException(
                    "Unsupported profile schema: " + schema +
                            " (expected: " + EXPECTED_SCHEMA + ") in " + path
            );
        }

        // Parse top-level fields
        String name = YamlMapUtil.getString(data, "name", path);
        String runtime = YamlMapUtil.getString(data, "runtime", path);

        // Parse secureChannel block
        Map<String, Object> scpData = YamlMapUtil.getMap(data, "secureChannel", path);
        ScpConfig secureChannel = parseScpConfig(scpData, path);
        PcscConfig pcsc = parsePcscConfig(data, path);

        return new Profile(name, runtime, secureChannel, pcsc);
    }

    /**
     * Parses the secureChannel section of a profile YAML.
     *
     * @param data secureChannel map from YAML
     * @param path path to YAML file (for error messages)
     * @return parsed {@link ScpConfig}
     */
    private static ScpConfig parseScpConfig(Map<String, Object> data, Path path) {
        String protocol = YamlMapUtil.getString(data, "protocol", path);
        String isdAidHex = YamlMapUtil.getString(data, "isdAid", path);
        int keyVersionNumber = YamlMapUtil.getInt(data, "keyVersionNumber", path);
        int keyIdentifier = YamlMapUtil.getInt(data, "keyIdentifier", path);
        int securityLevel = YamlMapUtil.getInt(data, "securityLevel", path);
        String encKeyHex = YamlMapUtil.getString(data, "encKey", path);
        String macKeyHex = YamlMapUtil.getString(data, "macKey", path);
        String dekKeyHex = YamlMapUtil.getString(data, "dekKey", path);

        try {
            byte[] isdAid = Util.parseHex(isdAidHex);
            byte[] encKey = Util.parseHex(encKeyHex);
            byte[] macKey = Util.parseHex(macKeyHex);
            byte[] dekKey = Util.parseHex(dekKeyHex);

            return new ScpConfig(
                    protocol,
                    isdAid,
                    keyVersionNumber,
                    keyIdentifier,
                    securityLevel,
                    encKey,
                    macKey,
                    dekKey
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid hex string in " + path + ": " + e.getMessage(), e);
        }
    }

    private static PcscConfig parsePcscConfig(Map<String, Object> data, Path path) {
        Object value = data.get("pcsc");
        if (value == null) {
            return PcscConfig.automatic();
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "Field 'pcsc' must be a map in " + path
                            + " (found: " + value.getClass().getSimpleName() + ")");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> pcscData = (Map<String, Object>) value;
        String readerName = (String) pcscData.get("reader");
        @SuppressWarnings("unchecked")
        List<String> excludedReaderNames = (List<String>) pcscData.getOrDefault("excludedReaders", List.of());
        return new PcscConfig(readerName, excludedReaderNames);
    }

}
