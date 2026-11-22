package app.aoki.cinpo.task;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.gp.scp.SecureChannelProfile;

/**
 * Base class for a card-level task of an annotation-defined CINPO task kind.
 *
 * <p>Implementations extend this class and implement {@link #run()}. The framework instantiates the
 * task with its public no-argument constructor, sets the protected dependency fields, and calls
 * {@link #run()} once. Task code reads the dependencies directly; it does not receive a {@code
 * TaskContext}.
 *
 * <p>The dependency fields are populated by {@link CardTaskLoader} before {@link #run()} is called.
 * Task implementations must not reassign them.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Success: returns normally.
 *   <li>Failure: throws an unchecked exception.
 * </ul>
 */
public abstract class CardTask {

  /** Live APDU channel for the card or simulator. */
  protected ApduChannel channel;

  /** Parsed manifest for the active project. */
  protected AppletManifest manifest;

  /** Active runtime profile. */
  protected Profile profile;

  /** Arguments passed after {@code --}. */
  protected TaskArguments taskArguments;

  /** Secure channel key material derived from {@link #profile}. */
  protected SecureChannelProfile secureChannelProfile;

  protected CardTask() {}

  /** Executes this card task. */
  public abstract void run();
}
