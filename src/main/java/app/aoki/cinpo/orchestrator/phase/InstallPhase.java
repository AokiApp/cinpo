package app.aoki.cinpo.orchestrator.phase;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.gp.GpInstaller;
import app.aoki.cinpo.gp.cap.CapLoader;
import app.aoki.cinpo.gp.cap.CapPackage;
import app.aoki.cinpo.gp.scp.SecureChannelProfile;
import app.aoki.cinpo.gp.scp.SecureChannelSession;
import app.aoki.cinpo.orchestrator.Orchestrator;
import app.aoki.cinpo.orchestrator.Phase;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static app.aoki.cinpo.apdu.ApduUtil.assertSwOk;

/**
 * Phase that installs a CAP file onto the card via GlobalPlatform.
 *
 * <p>This phase:
 * <ol>
 *   <li>Loads the CAP file from the classpath using {@link CapLoader}</li>
 *   <li>Opens a GlobalPlatform secure channel session (SCP02/SCP03)</li>
 *   <li>Authenticates with the Security Domain</li>
 *   <li>Optionally deletes existing applets (if deleteFirst is enabled)</li>
 *   <li>Installs the CAP file and creates applet instances via {@link GpInstaller}</li>
 * </ol>
 *
 * <h2>CAP File Location</h2>
 * CAP files must be on the classpath at {@code cap/<packageName>.cap}. The package
 * name is read from the applet manifest.
 *
 * <h2>Secure Channel</h2>
 * The secure channel configuration is read from the profile's
 * {@link Profile#secureChannel()} settings. The session is automatically closed
 * after installation completes.
 *
 * <h2>Delete-First Mode</h2>
 * If {@link Orchestrator#isDeleteFirst()} returns true, existing applet instances
 * and the load file are deleted before installation. This is useful for development
 * workflows where the applet is frequently reinstalled.
 *
 * @see GpInstaller
 * @see CapLoader
 * @see SecureChannelSession
 */
public final class InstallPhase implements Phase {

    private static final Logger LOG = Logger.getLogger(InstallPhase.class.getName());
 
    /**
     * Creates a new InstallPhase instance.
     */
    public InstallPhase() {
    }

    @Override
    public void execute(Orchestrator orchestrator) throws IOException {
        ApduChannel channel = orchestrator.requireChannel();
        AppletManifest manifest = orchestrator.getManifest();
        Profile profile = orchestrator.getProfile();

        String capResourcePath = "cap/" + manifest.packageName() + ".cap";
        LOG.fine(() -> "Loading CAP from classpath: " + capResourcePath);
        CapPackage cap = CapLoader.readFromClasspath(capResourcePath);
 
        SecureChannelProfile scpProfile = profile.secureChannel().toSecureChannelProfile();
        LOG.fine("Opening secure channel session");
        SecureChannelSession session = SecureChannelSession.create(channel, scpProfile);
        assertSwOk(channel.transmit(Iso7816Commands.selectDf(scpProfile.securityDomainAid())));
        session.authenticate();
        LOG.fine("Secure channel authenticated");
 
        try {
            List<GpInstaller.AppletEntry> gpApplets = convertToGpAppletEntries(manifest);
            LOG.fine(() -> "Installing package with " + gpApplets.size() + " applet entries");
            GpInstaller.install(
                    session,
                    manifest.loadFileAid(),
                    gpApplets,
                    cap,
                    orchestrator.isDeleteFirst()
            );
        } finally {
            LOG.fine("Closing secure channel session");
            session.close();
        }

        LOG.fine("Resetting APDU channel after installation");
        channel.reset();
    }

    /**
     * Converts config package AppletEntry list to GP installer AppletEntry list.
     *
     * <p>This helper method exists because config.AppletEntry is package-private
     * and cannot be directly accessed. We use the public accessors from
     * AppletManifest.applets() to extract the data.
     */
    private static List<GpInstaller.AppletEntry> convertToGpAppletEntries(AppletManifest manifest) {
        return manifest.applets().stream()
                .map(applet -> new GpInstaller.AppletEntry(
                        applet.id(),
                        applet.classAid(),  // classAid maps to executableModuleAid
                        applet.instanceAid(),
                        applet.privileges(),
                        null  // installParameters not supported in manifest.yaml
                ))
                .collect(Collectors.toList());
    }
}
