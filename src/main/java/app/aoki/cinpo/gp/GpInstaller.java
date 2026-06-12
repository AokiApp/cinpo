package app.aoki.cinpo.gp;

import static app.aoki.cinpo.apdu.ApduUtil.assertSwOk;

import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.gp.cap.CapPackage;
import app.aoki.cinpo.gp.scp.SecureChannelSession;
import app.aoki.cinpo.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Executes a basic GlobalPlatform applet installation flow over an authenticated secure channel.
 *
 * <p>The loading and installation process follows the two-phase model described in GPCS §9.3.1:
 * the <em>loading phase</em> transfers an Executable Load File to the card, and the
 * <em>installation phase</em> creates one or more Application instances from Executable Modules
 * within that file. As stated in the spec, "the GlobalPlatform Card Content loading process is
 * designed to allow the addition of code to Mutable Persistent Memory in the card", while "the
 * installation process is designed to allow the Card Issuer to make previously loaded application
 * code executable on the card."
 *
 * <p>All public methods in this class require an already-authenticated
 * {@link SecureChannelSession}. Callers must complete SCP authentication before invoking any
 * method here.
 *
 * @see SecureChannelSession
 */
public final class GpInstaller {

    private GpInstaller() {
    }

    /**
     * Performs a complete GlobalPlatform applet installation sequence (GPCS §9.3, §11.5, §11.6).
     *
     * <p>The installation proceeds in up to four steps:
     * <ol>
     *   <li><strong>Optional delete of existing instances</strong> – if {@code deleteFirst} is
     *       {@code true}, a DELETE command (§11.2, INS {@code E4}) is sent for each applet
     *       instance AID. Status words {@code 9000}, {@code 6A88} (Referenced data not found),
     *       and {@code 6A82} (Application not found) are all treated as success; any other SW
     *       causes an exception.</li>
     *   <li><strong>Optional delete of existing load file</strong> – if {@code deleteFirst} is
     *       {@code true}, a DELETE [object and related] command is sent for the load file AID,
     *       removing the Executable Load File together with any remaining associated
     *       Executable Modules.</li>
     *   <li><strong>INSTALL [for load] + LOAD blocks</strong> – an INSTALL command with P1 bit
     *       b2=1 (§11.5.2.1) announces the upcoming load file to the Security Domain, followed
     *       by one or more LOAD commands (INS {@code E8}, §11.6). "Multiple LOAD commands may be
     *       used to transfer a Load File to the card. Each LOAD command shall be numbered starting
     *       at {@code 00} and the numbering shall be strictly sequential" (§11.6.1). The last
     *       block is flagged so the card can finalise the Executable Load File and create
     *       GlobalPlatform Registry entries for it and each Executable Module.</li>
     *   <li><strong>INSTALL [for install and make selectable]</strong> – one INSTALL command per
     *       {@link AppletEntry} (P1 bits b3+b4=1, §11.5.2.1) supplies the Executable Load File
     *       AID, Executable Module AID, Application Instance AID, Privileges, and Install
     *       Parameters (tag {@code C9}, §11.5.2.3.7) to create and immediately select-enable each
     *       Application.</li>
     * </ol>
     *
     * <p><strong>Caution – authenticated session required.</strong> This method must be called
     * within an active, authenticated {@link SecureChannelSession}. Sending GP management
     * commands outside a secure channel will be rejected by the card.
     *
     * <p><strong>Caution – partial load risk.</strong> If any LOAD block command fails after the
     * INSTALL [for load] has already been accepted, the card may be left in an inconsistent state
     * (partial Executable Load File present). The caller is responsible for recovery, typically by
     * issuing a DELETE [object and related] on the load file AID.
     *
     * @param session      authenticated secure channel session used to transmit APDUs
     * @param loadFileAid  AID of the Executable Load File (package AID from the CAP file)
     * @param applets      one or more applet entries describing each Application to install
     * @param capPackage   the CAP package whose load blocks are transmitted via LOAD commands
     * @param deleteFirst  if {@code true}, attempt to delete existing applet instances and the
     *                     load file before installing; SW {@code 6A88}/{@code 6A82} are ignored
     */
    public static void install(
            SecureChannelSession session,
            byte[] loadFileAid,
            List<AppletEntry> applets,
            CapPackage capPackage,
            boolean deleteFirst) {
        Objects.requireNonNull(session);
        byte[] normalizedLoadFileAid = GpUtil.requireAid(loadFileAid);
        List<AppletEntry> normalizedApplets = normalizeApplets(applets);
        Objects.requireNonNull(capPackage);

        if (deleteFirst) {
            // Delete the Executable Load File together with all its related Applications
            // in a single DELETE [object and related] (P2=0x80) command.
            // Deleting by load file AID is sufficient and avoids per-instance DELETE
            // commands (P2=0x00) which some cards reject with SW=6985 when an applet is
            // already in a personalized or locked lifecycle state.
            deleteIfPresent(session, normalizedLoadFileAid, true);
        }

        try {
            assertSwOk(session.transmit(GpCommands.installForLoad(normalizedLoadFileAid, null)));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "INSTALL [for load] failed for package AID " + Util.toHex(normalizedLoadFileAid),
                    e);
        }

        List<byte[]> loadBlocks = capPackage.loadBlocks();
        for (int index = 0; index < loadBlocks.size(); index++) {
            try {
                int p1 = index == loadBlocks.size() - 1 ? GpCommands.LOAD_P1_LAST_BLOCK : GpCommands.LOAD_P1_MORE_BLOCKS;
                assertSwOk(session.transmit(GpCommands.load(p1, index, loadBlocks.get(index))));
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "LOAD failed at block " + index + " of " + loadBlocks.size()
                                + " for package AID " + Util.toHex(normalizedLoadFileAid),
                        e);
            }
        }

        for (AppletEntry applet : normalizedApplets) {
            try {
                assertSwOk(session.transmit(GpCommands.installForInstallAndMakeSelectable(
                        normalizedLoadFileAid,
                        applet.executableModuleAid(),
                        applet.instanceAid(),
                        applet.privileges(),
                        applet.installParameters())));
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "INSTALL [for install and make selectable] failed for applet '"
                                + applet.id() + "' classAID=" + Util.toHex(applet.executableModuleAid())
                                + " instanceAID=" + Util.toHex(applet.instanceAid()),
                        e);
            }
        }
    }

    /**
     * Sends a GlobalPlatform DELETE command for the given AID and silently ignores
     * "not found" responses (GPCS §11.2).
     *
     * <p>The DELETE command (INS {@code E4}) removes an Executable Load File, an Application, or
     * a Security Domain from the card. Reference control parameter P2 controls scope (§11.2.2.2):
     * <ul>
     *   <li>P2 b8=0 – <em>Delete object</em>: only the identified object is removed.</li>
     *   <li>P2 b8=1 – <em>Delete object and related objects</em>: the Executable Load File and
     *       all its associated Applications are removed in one operation.</li>
     * </ul>
     *
     * <p>The following status words are treated as non-fatal:
     * <ul>
     *   <li>{@code 9000} – command completed successfully.</li>
     *   <li>{@code 6A88} – Referenced data not found (object does not exist; safe to ignore).</li>
     *   <li>{@code 6A82} – Application not found (object does not exist; safe to ignore).</li>
     * </ul>
     * Any other SW causes an {@link IllegalStateException}.
     *
     * @param session    authenticated secure channel session
     * @param aid        AID of the object to delete
     * @param andRelated if {@code true}, uses DELETE [object and related] (P2 b8=1) to also
     *                   remove all Applications associated with the Executable Load File
     */
    private static void deleteIfPresent(SecureChannelSession session, byte[] aid, boolean andRelated) {
        ResponseApdu response = session.transmit(andRelated
                ? GpCommands.deleteAidAndRelated(aid)
                : GpCommands.deleteAid(aid));
        if (response.sw() == 0x9000 || response.sw() == 0x6A88 || response.sw() == 0x6A82) {
            return;
        }
        throw new IllegalStateException(
                "GlobalPlatform delete failed for AID " + Util.toHex(aid) + " with SW=" + String.format("%04X", response.sw()));
    }

    private static List<AppletEntry> normalizeApplets(List<AppletEntry> applets) {
        Objects.requireNonNull(applets);
        if (applets.isEmpty()) {
            throw new IllegalArgumentException("applets must not be empty");
        }
        return List.copyOf(new ArrayList<>(applets));
    }

    /**
     * Describes a single applet to be installed from an Executable Load File.
     *
     * <p>Each field maps to a parameter in the INSTALL [for install and make selectable]
     * command data field (GPCS §11.5.2.3, Table 11-43):
     *
     * <ul>
     *   <li>{@link #id} – human-readable identifier used in manifest files and log messages;
     *       not sent to the card.</li>
     *   <li>{@link #executableModuleAid} – the <em>Executable Module AID</em> (class AID) that
     *       identifies the specific class within the CAP file to instantiate.</li>
     *   <li>{@link #instanceAid} – the <em>Application Instance AID</em> that will be registered
     *       in the GlobalPlatform Registry and used for ISO 7816 SELECT commands.</li>
     *   <li>{@link #privileges} – Application Privileges, 1 or 3 bytes as defined in §11.1.2.
     *       "When receiving Privileges coded on one byte, the OPEN shall extend the privileges to
     *       3 bytes and assign the second and third bytes with the default values" (§11.5.2.3).</li>
     *   <li>{@link #installParameters} – optional Application Specific Parameters carried in
     *       Install Parameters tag {@code C9} (§11.5.2.3.7). The raw bytes here are the
     *       <em>content</em> of tag {@code C9}; the tag and length are added by
     *       {@link GpCommands}. May be {@code null} if no parameters are required.</li>
     * </ul>
     */
    public record AppletEntry(
            String id,
            byte[] executableModuleAid,
            byte[] instanceAid,
            byte[] privileges,
            byte[] installParameters) {

        public AppletEntry {
            id = id == null ? "<unnamed>" : id;
            executableModuleAid = GpUtil.requireAid(executableModuleAid);
            instanceAid = GpUtil.requireAid(instanceAid);
            privileges = GpUtil.normalizePrivileges(privileges);
            installParameters = installParameters == null
                    ? null
                    : Arrays.copyOf(installParameters, installParameters.length);
        }

        @Override
        public byte[] executableModuleAid() {
            return Arrays.copyOf(executableModuleAid, executableModuleAid.length);
        }

        @Override
        public byte[] instanceAid() {
            return Arrays.copyOf(instanceAid, instanceAid.length);
        }

        @Override
        public byte[] privileges() {
            return Arrays.copyOf(privileges, privileges.length);
        }

        @Override
        public byte[] installParameters() {
            return installParameters == null
                    ? null
                    : Arrays.copyOf(installParameters, installParameters.length);
        }
    }
}
