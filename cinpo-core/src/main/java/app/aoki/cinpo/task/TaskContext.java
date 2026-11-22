package app.aoki.cinpo.task;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.gp.scp.SecureChannelProfile;

/**
 * Bundles the runtime dependencies that {@link CardTaskLoader} assigns to a {@link CardTask} before
 * {@link CardTask#run()} is called.
 *
 * <p>A {@code TaskContext} carries the core runtime dependencies that card tasks typically need:
 *
 * <ul>
 *   <li>{@link ApduChannel} - for sending APDUs to the card
 *   <li>{@link AppletManifest} - metadata about installed applets
 *   <li>{@link Profile} - runtime configuration (runtime type, secure channel)
 *   <li>{@link TaskArguments} - raw template-specific arguments passed after {@code --}
 * </ul>
 *
 * <p>Tasks extend {@link CardTask} and use the inherited fields:
 *
 * <pre>{@code
 * @CardTaskDef("provision")
 * public final class MyProvisionTask extends CardTask {
 *     @Override
 *     public void run() {
 *         channel.transmit(...);
 *         byte[] aid = manifest.applets().get(0).instanceAid();
 *     }
 * }
 * }</pre>
 *
 * <h2>Dependencies exposed on CardTask</h2>
 *
 * The loader assigns the following fields (see {@link CardTaskLoader}):
 *
 * <ul>
 *   <li>{@link ApduChannel}
 *   <li>{@link AppletManifest}
 *   <li>{@link Profile}
 *   <li>{@link SecureChannelProfile}
 *   <li>{@link TaskArguments}
 * </ul>
 *
 * @param channel the APDU channel for communicating with the card
 * @param manifest the applet manifest containing AIDs and metadata
 * @param profile the runtime profile containing secure channel configuration
 * @param taskArguments raw template-specific arguments passed after {@code --}
 * @see CardTaskLoader
 * @see CardTask
 */
public record TaskContext(
    ApduChannel channel, AppletManifest manifest, Profile profile, TaskArguments taskArguments) {
  /**
   * Converts the profile's secure channel configuration to a {@link SecureChannelProfile}.
   *
   * <p>The loader uses this value to populate {@link CardTask#secureChannelProfile}.
   *
   * @return a {@link SecureChannelProfile} derived from the profile
   */
  public SecureChannelProfile toSecureChannelProfile() {
    return profile.secureChannel().toSecureChannelProfile();
  }
}
