package app.aoki.cinpo.config;

import app.aoki.cinpo.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@link Profile} configurations from YAML files.
 *
 * <h2>Profile Resolution Order</h2>
 *
 * <ol>
 *   <li>If {@code nameOrPath} is an absolute path, load from that file
 *   <li>If {@code nameOrPath} contains "/" or ends with ".yaml", load that path relative to the
 *       project directory
 *   <li>Otherwise, check {@code profile/{nameOrPath}.yaml} below the project directory
 *   <li>Otherwise, check {@code profile/{nameOrPath}.yaml} on the classpath. This is how an
 *       appliance JAR carries the profiles of the project it was built from
 *   <li>If not found, throw {@link IOException} with every location that was searched
 * </ol>
 *
 * <p>There are no builtin profiles and no profile registry in the framework: a profile is always a
 * YAML file, either in the project or on the classpath. The CLI defaults to the profile name {@code
 * jcdksim}, which resolves through the same lookup as any other name. A project that does not use
 * the simulator is expected to name its own profile; if it leaves the default in place, resolution
 * or adapter lookup fails with a clear error.
 *
 * <h2>YAML Schema</h2>
 *
 * <pre>
 * schema: cinpo.profile.v1
 * name: mycard
 * runtime: pcsc
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
 *   encKey: &lt;your 32-byte hex ENC key&gt;
 *   macKey: &lt;your 32-byte hex MAC key&gt;
 *   dekKey: &lt;your 32-byte hex DEK key&gt;
 * </pre>
 *
 * @see Profile
 */
public final class ProfileLoader {

  private static final String EXPECTED_SCHEMA = "cinpo.profile.v1";
  private static final String PROFILE_DIR = "profile";
  private static final String CLASSPATH_PROFILE_PREFIX = "profile/";

  private ProfileLoader() {
    // Utility class - no instantiation
  }

  /**
   * Loads a profile by name or file path, relative to the current directory.
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

    String classpathResource = CLASSPATH_PROFILE_PREFIX + nameOrPath + ".yaml";
    ClassLoader loader = ProfileLoader.class.getClassLoader();
    try (InputStream resource = loader.getResourceAsStream(classpathResource)) {
      if (resource != null) {
        return loadFromYaml(
            new String(resource.readAllBytes(), StandardCharsets.UTF_8),
            "classpath:" + classpathResource);
      }
    }

    String searched =
        candidates.stream()
            .map(path -> path.toAbsolutePath().normalize().toString())
            .reduce((left, right) -> left + "\n  - " + right)
            .orElse("(no path)");
    throw new IOException(
        "Profile not found: "
            + nameOrPath
            + "\nSearched:\n  - "
            + searched
            + "\n  - classpath:"
            + classpathResource);
  }

  /**
   * Resolves a profile name or path to every file path that should be checked.
   *
   * @param nameOrPath profile name or path
   * @param projectDirectory application project directory used for relative profile lookup
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

    return List.of(
        normalizedProjectDirectory.resolve(PROFILE_DIR).resolve(nameOrPath + ".yaml").normalize());
  }

  private static boolean isPathLike(String nameOrPath) {
    return nameOrPath.contains("/")
        || nameOrPath.contains("\\")
        || nameOrPath.endsWith(".yaml")
        || nameOrPath.endsWith(".yml");
  }

  /**
   * Loads and parses a profile YAML file.
   *
   * @param path path to YAML file
   * @return loaded {@link Profile}
   * @throws IOException if file cannot be read
   */
  private static Profile loadFromFile(Path path) throws IOException {
    return loadFromYaml(Files.readString(path), path);
  }

  /**
   * Parses profile YAML text, whether it came from a file or a classpath resource.
   *
   * @param yamlContent YAML text
   * @param source label used in error messages
   * @return loaded {@link Profile}
   */
  private static Profile loadFromYaml(String yamlContent, Object source) {
    Yaml yaml = new Yaml();
    Map<String, Object> data = yaml.load(yamlContent);

    if (data == null) {
      throw new IllegalArgumentException("Profile YAML is empty: " + source);
    }

    String schema = YamlMapUtil.getString(data, "schema", source);
    if (!EXPECTED_SCHEMA.equals(schema)) {
      throw new IllegalArgumentException(
          "Unsupported profile schema: "
              + schema
              + " (expected: "
              + EXPECTED_SCHEMA
              + ") in "
              + source);
    }

    String name = YamlMapUtil.getString(data, "name", source);
    String runtime = YamlMapUtil.getString(data, "runtime", source);
    Map<String, Object> scpData = YamlMapUtil.getMap(data, "secureChannel", source);
    ScpConfig secureChannel = parseScpConfig(scpData, source);
    PcscConfig pcsc = parsePcscConfig(data, source);

    return new Profile(name, runtime, secureChannel, pcsc);
  }

  /**
   * Parses the secureChannel section of a profile YAML.
   *
   * @param data secureChannel map from YAML
   * @param source label used in error messages
   * @return parsed {@link ScpConfig}
   */
  private static ScpConfig parseScpConfig(Map<String, Object> data, Object source) {
    String protocol = YamlMapUtil.getString(data, "protocol", source);
    String isdAidHex = YamlMapUtil.getString(data, "isdAid", source);
    int keyVersionNumber = YamlMapUtil.getInt(data, "keyVersionNumber", source);
    int keyIdentifier = YamlMapUtil.getInt(data, "keyIdentifier", source);
    int securityLevel = YamlMapUtil.getInt(data, "securityLevel", source);
    String encKeyHex = YamlMapUtil.getString(data, "encKey", source);
    String macKeyHex = YamlMapUtil.getString(data, "macKey", source);
    String dekKeyHex = YamlMapUtil.getString(data, "dekKey", source);

    try {
      byte[] isdAid = Util.parseHex(isdAidHex);
      byte[] encKey = Util.parseHex(encKeyHex);
      byte[] macKey = Util.parseHex(macKeyHex);
      byte[] dekKey = Util.parseHex(dekKeyHex);

      return new ScpConfig(
          protocol, isdAid, keyVersionNumber, keyIdentifier, securityLevel, encKey, macKey, dekKey);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid hex string in " + source + ": " + e.getMessage(), e);
    }
  }

  private static PcscConfig parsePcscConfig(Map<String, Object> data, Object source) {
    Object value = data.get("pcsc");
    if (value == null) {
      return PcscConfig.automatic();
    }
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(
          "Field 'pcsc' must be a map in "
              + source
              + " (found: "
              + value.getClass().getSimpleName()
              + ")");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> pcscData = (Map<String, Object>) value;
    String readerName = (String) pcscData.get("reader");
    @SuppressWarnings("unchecked")
    List<String> excludedReaderNames =
        (List<String>) pcscData.getOrDefault("excludedReaders", List.of());
    return new PcscConfig(readerName, excludedReaderNames);
  }
}
