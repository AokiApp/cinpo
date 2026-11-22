package app.aoki.cinpo.config;

import java.util.Objects;

/**
 * Top-level configuration profile for card runtime and secure channel parameters.
 *
 * <p>A {@code Profile} encapsulates:
 *
 * <ul>
 *   <li>Profile name (e.g., "card1")
 *   <li>Runtime adapter identifier (e.g. "jcdksim", "jcardengine", or "pcsc")
 *   <li>Secure channel configuration for GlobalPlatform operations
 * </ul>
 *
 * <p>Profiles can be loaded from:
 *
 * <ul>
 *   <li>YAML files in the project-local {@code profile/} directory
 *   <li>The same YAML path on the classpath (an appliance JAR carries its profiles this way)
 *   <li>Absolute or relative file paths
 * </ul>
 *
 * @param name Profile name (e.g., "card1")
 * @param runtime Runtime adapter identifier (e.g. "jcdksim", "jcardengine", or "pcsc")
 * @param secureChannel GlobalPlatform Secure Channel Protocol configuration
 * @param pcsc PC/SC reader selection configuration
 * @see ProfileLoader
 */
public record Profile(String name, String runtime, ScpConfig secureChannel, PcscConfig pcsc) {
  public Profile(String name, String runtime, ScpConfig secureChannel) {
    this(name, runtime, secureChannel, PcscConfig.automatic());
  }

  public Profile {
    Objects.requireNonNull(name);
    Objects.requireNonNull(runtime);
    Objects.requireNonNull(secureChannel);
    pcsc = Objects.requireNonNullElseGet(pcsc, PcscConfig::automatic);

    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (runtime.isBlank()) {
      throw new IllegalArgumentException("runtime must not be blank");
    }
  }
}
