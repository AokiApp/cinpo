package app.aoki.cinpo.task;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.gp.scp.SecureChannelProfile;

/**
 * Encapsulates dependencies that can be injected into {@link CardTask} instances.
 *
 * <p>A {@code TaskContext} bundles the core runtime dependencies that card tasks
 * typically need:
 * <ul>
 *   <li>{@link ApduChannel} - for sending APDUs to the card</li>
 *   <li>{@link AppletManifest} - metadata about installed applets</li>
 *   <li>{@link Profile} - runtime configuration (runtime type, secure channel)</li>
 *   <li>{@link TaskArguments} - raw template-specific arguments passed after {@code --}</li>
 * </ul>
 *
 * <p>Tasks declare dependencies via {@link Inject} annotations on fields:
 * <pre>{@code
 * @CardTaskDef("provision")
 * public class MyProvisionTask implements CardTask {
 *     @Inject
 *     private ApduChannel channel;
 *
 *     @Inject
 *     private AppletManifest manifest;
 *
 *     @Override
 *     public void run() {
 *         // Use injected dependencies
 *     }
 * }
 * }</pre>
 *
 * <h2>Supported Injectable Types</h2>
 * The following types can be injected via {@code @Inject} (see {@link CardTaskLoader}):
 * <ul>
 *   <li>{@link ApduChannel}</li>
 *   <li>{@link AppletManifest}</li>
 *   <li>{@link Profile}</li>
 *   <li>{@link SecureChannelProfile}</li>
 *   <li>{@link TaskArguments}</li>
 * </ul>
 *
 * @param channel       the APDU channel for communicating with the card
 * @param manifest      the applet manifest containing AIDs and metadata
 * @param profile       the runtime profile containing secure channel configuration
 * @param taskArguments raw template-specific arguments passed after {@code --}
 * @see CardTaskLoader
 * @see Inject
 * @see CardTask
 */
public record TaskContext(
        ApduChannel channel,
        AppletManifest manifest,
        Profile profile,
        TaskArguments taskArguments
) {
    /**
     * Converts the profile's secure channel configuration to a {@link SecureChannelProfile}.
     *
     * <p>This is a convenience method for tasks that need to establish their own
     * secure channel sessions.
     *
     * @return a {@link SecureChannelProfile} derived from the profile
     */
    public SecureChannelProfile toSecureChannelProfile() {
        return profile.secureChannel().toSecureChannelProfile();
    }
}
