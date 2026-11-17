package app.aoki.cinpo.gp;

import app.aoki.cinpo.apdu.BerTlv;
import app.aoki.cinpo.apdu.CommandApdu;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Factory for GlobalPlatform APDU command messages as defined in
 * GlobalPlatform Card Specification (GPCS) v2.3.1.49 §11 and
 * SCP03 Amendment D (Amd D) v1.1.2 §7.
 *
 * <p>Each factory method assembles a {@link CommandApdu} whose CLA, INS, P1, P2,
 * and data field conform to the relevant GPCS table.  CLA byte coding follows
 * GPCS §11.1.4 (Table 11-11): {@code 0x80} denotes a GlobalPlatform command on
 * the basic logical channel with no secure messaging; {@code 0x84} adds
 * GlobalPlatform-proprietary secure messaging (bits b4b3 = 01).
 *
 * <p>Supported commands:
 * <ul>
 *   <li>INITIALIZE UPDATE  – Amd D §7.1.1, Table 7-2</li>
 *   <li>EXTERNAL AUTHENTICATE – Amd D §7.1.2, Table 7-5</li>
 *   <li>GET STATUS         – GPCS §11.4, Table 11-33</li>
 *   <li>INSTALL            – GPCS §11.5, Tables 11-40 through 11-43</li>
 *   <li>LOAD               – GPCS §11.6, Tables 11-56 through 11-58</li>
 *   <li>DELETE             – GPCS §11.2, Tables 11-20 through 11-23</li>
 * </ul>
 *
 * <p>This class is a pure utility class and cannot be instantiated.
 */
public final class GpCommands {

    // -------------------------------------------------------------------------
    // INS byte constants
    // -------------------------------------------------------------------------

    /** GPCS §11.5 Table 11-40: INS byte for the INSTALL command ({@code 0xE6}). */
    public static final int INS_INSTALL = 0xE6;

    /** GPCS §11.6 Table 11-56: INS byte for the LOAD command ({@code 0xE8}). */
    public static final int INS_LOAD = 0xE8;

    /** GPCS §11.2 Table 11-20: INS byte for the DELETE command ({@code 0xE4}). */
    public static final int INS_DELETE = 0xE4;

    /** GPCS §11.4 Table 11-33: INS byte for the GET STATUS command ({@code 0xF2}). */
    public static final int INS_GET_STATUS = 0xF2;

    /**
     * Amd D §7.1.1 Table 7-2: INS byte for the INITIALIZE UPDATE command ({@code 0x50}).
     * CLA for this command is {@code 0x80}-{@code 0x83} per GPCS §11.1.4.
     */
    public static final int INS_INITIALIZE_UPDATE = 0x50;

    /**
     * Amd D §7.1.2 Table 7-5: INS byte for the EXTERNAL AUTHENTICATE command ({@code 0x82}).
     * CLA for this command is {@code 0x84}-{@code 0x87} (GlobalPlatform secure-messaging format)
     * per GPCS §11.1.4 Table 11-11.
     */
    public static final int INS_EXTERNAL_AUTHENTICATE = 0x82;

    // -------------------------------------------------------------------------
    // INSTALL P1 constants – GPCS §11.5.2.1 Table 11-41
    // -------------------------------------------------------------------------

    /**
     * GPCS §11.5.2.1 Table 11-41: P1 bit b2=1 – INSTALL [for load].
     * Indicates that a Load File is to be loaded; a subsequent LOAD command sequence is expected.
     */
    public static final int INSTALL_P1_FOR_LOAD = 0x02;

    /**
     * GPCS §11.5.2.1 Table 11-41: P1 bit b3=1 – INSTALL [for install].
     * Indicates that the Application shall be installed.
     */
    public static final int INSTALL_P1_FOR_INSTALL = 0x04;

    /**
     * GPCS §11.5.2.1 Table 11-41: P1 bit b4=1 – INSTALL [for make selectable].
     * Indicates that the Application shall be made selectable.
     */
    public static final int INSTALL_P1_FOR_MAKE_SELECTABLE = 0x08;

    /**
     * GPCS §11.5.2.1 Table 11-41: P1 bits b4=1 and b3=1 ({@code 0x0C}) –
     * INSTALL [for install and make selectable].
     * A combination of the [for install] and [for make selectable] options.
     */
    public static final int INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE = 0x0C;

    // -------------------------------------------------------------------------
    // INSTALL P2 constants – GPCS §11.5.2.2
    // -------------------------------------------------------------------------

    /**
     * GPCS §11.5.2.2: P2={@code 0x00} – no additional information provided.
     * Used for standalone INSTALL commands (not part of a combined sequence).
     */
    public static final int INSTALL_P2_NONE = 0x00;

    /**
     * GPCS §11.5.2.2: P2={@code 0x01} – beginning of the combined
     * Load, Install and Make Selectable process.
     */
    public static final int INSTALL_P2_BEGIN_COMBINED_LOAD_INSTALL = 0x01;

    /**
     * GPCS §11.5.2.2: P2={@code 0x03} – end of the combined
     * Load, Install and Make Selectable process.
     */
    public static final int INSTALL_P2_END_COMBINED_LOAD_INSTALL = 0x03;

    // -------------------------------------------------------------------------
    // LOAD P1 constants – GPCS §11.6.2.1 Table 11-57
    // -------------------------------------------------------------------------

    /**
     * GPCS §11.6.2.1 Table 11-57: P1 bit b8=0 ({@code 0x00}) – more blocks follow.
     * Used for every LOAD block that is not the last in the sequence.
     */
    public static final int LOAD_P1_MORE_BLOCKS = 0x00;

    /**
     * GPCS §11.6.2.1 Table 11-57: P1 bit b8=1 ({@code 0x80}) – last block in the LOAD sequence.
     * After receiving the last block the card executes any processes identified in the preceding
     * INSTALL [for load] command.
     */
    public static final int LOAD_P1_LAST_BLOCK = 0x80;

    // -------------------------------------------------------------------------
    // DELETE P1/P2 constants – GPCS §11.2.2.1 Table 11-21 / §11.2.2.2 Table 11-22
    // -------------------------------------------------------------------------

    /**
     * GPCS §11.2.2.1 Table 11-21: P1 bit b8=0 ({@code 0x00}) –
     * last (or only) DELETE command in the sequence.
     */
    public static final int DELETE_P1_LAST_OR_ONLY = 0x00;

    /**
     * GPCS §11.2.2.1 Table 11-21: P1 bit b8=1 ({@code 0x80}) –
     * more DELETE commands follow (command chaining).
     */
    public static final int DELETE_P1_MORE_COMMANDS = 0x80;

    /**
     * GPCS §11.2.2.2 Table 11-22: P2 bit b8=0 ({@code 0x00}) –
     * delete the specified object only (Application or Executable Load File).
     *
     * @see #DELETE_P2_DELETE_OBJECT_AND_RELATED
     */
    public static final int DELETE_P2_DELETE_OBJECT = 0x00;

    /**
     * GPCS §11.2.2.2 Table 11-22: P2 bit b8=1 ({@code 0x80}) –
     * delete the Executable Load File and all its related Applications simultaneously.
     * Per §11.2.2.3.1: only the AID of the Executable Load File shall be provided in the
     * data field when this P2 value is used.
     *
     * @see #DELETE_P2_DELETE_OBJECT
     */
    public static final int DELETE_P2_DELETE_OBJECT_AND_RELATED = 0x80;

    // -------------------------------------------------------------------------
    // Private tag constants
    // -------------------------------------------------------------------------

    /** GPCS §11.2.2.3.1 Table 11-23 / §11.4.2.3 Table 11-35: BER-TLV tag for an AID ({@code 0x4F}). */
    private static final int TLV_TAG_AID = 0x4F;

    private GpCommands() {
    }

    // -------------------------------------------------------------------------
    // Public command-factory methods
    // -------------------------------------------------------------------------

    /**
     * Builds an INITIALIZE UPDATE command per Amd D §7.1.1, Table 7-2.
     *
     * <p>The command initiates a new Secure Channel Session.  The card returns a
     * response containing key diversification data (10 bytes), key information
     * (3 bytes), card challenge (8 bytes), card cryptogram (8 bytes), and an
     * optional Sequence Counter (3 bytes, present only for pseudo-random challenge
     * generation – Amd D §7.1.1.6 Table 7-3).
     *
     * @implSpec CLA={@code 0x80}, INS={@code 0x50} (GPCS §11.1.4 Table 11-11 + Amd D §7.1.1).
     *           Le={@code 0} (return all available response data).
     *
     * @apiNote  <strong>SCP03 caveat:</strong> Per Amd D §7.1.1.4, P2 (Key Identifier)
     *           <em>shall always</em> be {@code 0x00} when using SCP03.  Passing any other
     *           value as {@code keyIdentifier} will result in a non-compliant command.
     *
     * @param keyVersionNumber  P1 – Key Version Number within the Security Domain's key set
     *                          (Amd D §7.1.1.3).  Pass {@code 0} to let the Security Domain
     *                          choose the first available key.
     * @param keyIdentifier     P2 – Key Identifier.  <strong>Must be {@code 0x00} for SCP03</strong>
     *                          (Amd D §7.1.1.4).
     * @param hostChallenge     8-byte host challenge chosen by the off-card entity; should be
     *                          unique to each session (Amd D §7.1.1.5).
     * @return the INITIALIZE UPDATE {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code hostChallenge} is {@code null} or not exactly
     *                                  8 bytes.
     */
    public static CommandApdu initializeUpdate(int keyVersionNumber, int keyIdentifier, byte[] hostChallenge) {
        requireLength(hostChallenge, 8);
        return new CommandApdu(0x80, INS_INITIALIZE_UPDATE, keyVersionNumber, keyIdentifier, hostChallenge, 256);
    }

    /**
     * Builds an EXTERNAL AUTHENTICATE command per Amd D §7.1.2, Table 7-5.
     *
     * <p>This command authenticates the host to the card and establishes the security
     * level for all subsequent commands in the Secure Channel Session.  It must be
     * preceded by a successful INITIALIZE UPDATE (Amd D §7.1.2.1).
     *
     * @implSpec CLA={@code 0x84} (GlobalPlatform proprietary secure messaging per
     *           GPCS §11.1.4 Table 11-11, b4b3=01).  INS={@code 0x82}, P2={@code 0x00}
     *           always (Amd D §7.1.2.4).  Lc={@code 0x10}: 8-byte host cryptogram + 8-byte MAC.
     *
     * @param securityLevel P1 – requested security level for all subsequent secure-channel commands
     *                      (Amd D §7.1.2.3 Table 7-6).  Common values:
     *                      <ul>
     *                        <li>{@code 0x00} – no secure messaging</li>
     *                        <li>{@code 0x01} – C-MAC</li>
     *                        <li>{@code 0x03} – C-DECRYPTION + C-MAC</li>
     *                        <li>{@code 0x11} – C-MAC + R-MAC</li>
     *                        <li>{@code 0x13} – C-DECRYPTION + C-MAC + R-MAC</li>
     *                        <li>{@code 0x33} – C-DECRYPTION + R-ENCRYPTION + C-MAC + R-MAC</li>
     *                      </ul>
     * @param data          host cryptogram concatenated with the APDU command MAC
     *                      (Amd D §7.1.2.5); typically 16 bytes total.
     * @return the EXTERNAL AUTHENTICATE {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code data} is {@code null} or empty.
     */
    public static CommandApdu externalAuthenticate(int securityLevel, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data must not be null or empty");
        }
        return new CommandApdu(0x84, INS_EXTERNAL_AUTHENTICATE, securityLevel, 0x00, Arrays.copyOf(data, data.length));
    }

    /**
     * Builds a GET STATUS command per GPCS §11.4, Table 11-33.
     *
     * <p>GET STATUS retrieves registry information from the card.  Consecutive calls
     * with {@code p2} bit b1=1 (get next occurrence) retrieve additional results when
     * the previous response returned SW={@code 63 10} (more data available,
     * GPCS §11.4.3.2 Table 11-38).
     *
     * @implSpec CLA={@code 0x80}, INS={@code 0xF2}.  Le={@code 0} (return all data).
     *           The {@code criteria} data field must contain at minimum a TLV-coded AID
     *           search qualifier (tag {@code 4F}); use {@code 4F 00} to match all
     *           objects of the type selected by P1 (GPCS §11.4.2.3 Table 11-35).
     *
     * @param p1       request type (GPCS §11.4.2.1):
     *                 <ul>
     *                   <li>{@code 0x80} – Issuer Security Domain only</li>
     *                   <li>{@code 0x40} – Applications and Supplementary Security Domains</li>
     *                   <li>{@code 0x20} – Executable Load Files only</li>
     *                   <li>{@code 0x10} – Executable Load Files and their Executable Modules</li>
     *                 </ul>
     * @param p2       occurrence control (GPCS §11.4.2.2 Table 11-34):
     *                 {@code 0x02} = get first/all occurrence(s) with new-format response;
     *                 {@code 0x03} = get next occurrence(s) with new-format response.
     * @param criteria TLV-coded search data field (mandatory); must not be {@code null}.
     * @return the GET STATUS {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code criteria} is {@code null}.
     */
    public static CommandApdu getStatus(int p1, int p2, byte[] criteria) {
        return new CommandApdu(0x80, INS_GET_STATUS, p1, p2, copyNonNull(criteria), 256);
    }

    /**
     * Builds a raw INSTALL command per GPCS §11.5, Table 11-40.
     *
     * <p>Callers should prefer the higher-level helpers ({@link #installForLoad},
     * {@link #installForInstallAndMakeSelectable}, etc.) which construct the correct
     * data field encoding.  This method is exposed for unusual P1/P2 combinations.
     *
     * @implSpec CLA={@code 0x80}, INS={@code 0xE6}.  Le={@code 0}.
     *
     * @param p1   reference control parameter P1 (GPCS §11.5.2.1 Table 11-41);
     *             see {@code INSTALL_P1_*} constants.
     * @param p2   reference control parameter P2 (GPCS §11.5.2.2);
     *             see {@code INSTALL_P2_*} constants.
     * @param data fully-encoded INSTALL data field; must not be {@code null}.
     * @return the INSTALL {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code data} is {@code null}.
     */
    public static CommandApdu install(int p1, int p2, byte[] data) {
        return new CommandApdu(0x80, INS_INSTALL, p1, p2, copyNonNull(data), 256);
    }

    /**
     * Builds an INSTALL [for load] command per GPCS §11.5.2.3.1, Table 11-42.
     *
     * <p>This command must precede the LOAD command sequence.  It registers the
     * intended Load File AID and optional Security Domain association with the card.
     *
     * <p><strong>Data field encoding (Table 11-42):</strong>
     * <ul>
     *   <li>Fields 1–3 (Load File AID length + value, Security Domain AID length + value,
     *       Load File Data Block Hash length + value) use <em>short (1-byte) LV</em> encoding.</li>
     *   <li>Fields 4–5 (Load Parameters length + value, Load Token length + value) use
     *       <em>BER-TLV</em> length encoding (1 byte for lengths ≤127, 2 bytes for ≤255,
     *       3 bytes for ≤65535) per GPCS §11.5.2.3 and §11.1.5.</li>
     * </ul>
     * Both Load Parameters and Load Token are omitted (empty, length={@code 0x00}) by this
     * implementation.
     *
     * @implSpec P1={@link #INSTALL_P1_FOR_LOAD} ({@code 0x02}), P2={@link #INSTALL_P2_NONE}
     *           ({@code 0x00}).
     *
     * @param loadFileAid        AID of the Load File to be loaded (5–16 bytes); must not be
     *                           {@code null}.
     * @param securityDomainAid  AID of the target Security Domain (5–16 bytes), or {@code null}
     *                           to use the default Security Domain (encoded as length {@code 0x00}).
     * @return the INSTALL [for load] {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code loadFileAid} is {@code null} or has invalid length.
     */
    public static CommandApdu installForLoad(byte[] loadFileAid, byte[] securityDomainAid) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        // §11.5.2.3 Table 11-42: fields 1-3 use short (1-byte) LV encoding
        BerTlv.appendShortLv(data, GpUtil.requireAid(loadFileAid));
        BerTlv.appendShortLv(data, GpUtil.normalizeOptionalAid(securityDomainAid));
        BerTlv.appendShortLv(data, new byte[0]); // Load File Data Block Hash – omitted
        // §11.5.2.3 Table 11-42: fields 4-5 use BER-TLV length encoding
        BerTlv.appendBerLv(data, new byte[0]);   // Load Parameters – omitted
        BerTlv.appendBerLv(data, new byte[0]);   // Load Token – omitted
        return install(INSTALL_P1_FOR_LOAD, INSTALL_P2_NONE, data.toByteArray());
    }

    /**
     * Builds an INSTALL [for install and make selectable] command per GPCS §11.5.2.3.2,
     * Table 11-43.
     *
     * <p>This is a convenience wrapper for the common combined operation; it delegates to
     * {@link #installForApplication} with P1={@link #INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE}.
     *
     * @implSpec P1={@link #INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE} ({@code 0x0C}),
     *           P2={@link #INSTALL_P2_NONE} ({@code 0x00}).
     *
     * @param loadFileAid        AID of the Executable Load File (5–16 bytes); must not be
     *                           {@code null}.
     * @param executableModuleAid AID of the Executable Module within the Load File (5–16 bytes);
     *                           must not be {@code null}.
     * @param instanceAid        AID for the new Application instance (5–16 bytes); must not be
     *                           {@code null}.
     * @param privileges         Privileges byte(s) for the new Application (1 or 3 bytes per
     *                           GPCS §11.1.2); must not be {@code null}.
     * @param installParameters  Install Parameters field (tag {@code C9} sub-TLV content), or
     *                           {@code null} / empty to use default parameters.
     * @return the INSTALL [for install and make selectable] {@link CommandApdu}.
     * @throws IllegalArgumentException if any mandatory AID argument is {@code null} or invalid.
     */
    public static CommandApdu installForInstallAndMakeSelectable(
            byte[] loadFileAid,
            byte[] executableModuleAid,
            byte[] instanceAid,
            byte[] privileges,
            byte[] installParameters) {
        return installForApplication(
                INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE,
                loadFileAid,
                executableModuleAid,
                instanceAid,
                privileges,
                installParameters);
    }

    /**
     * Builds an INSTALL [for install] command per GPCS §11.5.2.3.2, Table 11-43.
     *
     * <p>Installs the Application but does not make it immediately selectable.
     * Use {@link #installForInstallAndMakeSelectable} to install and make selectable
     * in a single command.
     *
     * @implSpec P1={@link #INSTALL_P1_FOR_INSTALL} ({@code 0x04}),
     *           P2={@link #INSTALL_P2_NONE} ({@code 0x00}).
     *
     * @param loadFileAid        AID of the Executable Load File (5–16 bytes); must not be
     *                           {@code null}.
     * @param executableModuleAid AID of the Executable Module (5–16 bytes); must not be
     *                           {@code null}.
     * @param instanceAid        AID for the new Application instance (5–16 bytes); must not be
     *                           {@code null}.
     * @param privileges         Privileges byte(s) (1 or 3 bytes per GPCS §11.1.2); must not be
     *                           {@code null}.
     * @param installParameters  Install Parameters content, or {@code null} / empty.
     * @return the INSTALL [for install] {@link CommandApdu}.
     * @throws IllegalArgumentException if any mandatory AID argument is {@code null} or invalid.
     */
    public static CommandApdu installForInstall(
            byte[] loadFileAid,
            byte[] executableModuleAid,
            byte[] instanceAid,
            byte[] privileges,
            byte[] installParameters) {
        return installForApplication(
                INSTALL_P1_FOR_INSTALL,
                loadFileAid,
                executableModuleAid,
                instanceAid,
                privileges,
                installParameters);
    }

    /**
     * Builds a LOAD command per GPCS §11.6, Table 11-56.
     *
     * <p>A Load File is divided into numbered blocks and transmitted via consecutive LOAD
     * commands.  Block numbering starts at {@code 0x00} and increments strictly by one
     * (GPCS §11.6.1).  The final block must have P1 bit b8=1 ({@link #LOAD_P1_LAST_BLOCK});
     * all preceding blocks use P1={@link #LOAD_P1_MORE_BLOCKS} ({@code 0x00}).
     *
     * @implSpec CLA={@code 0x80}, INS={@code 0xE8}.  Le={@code 0}.
     *
     * @param p1          reference control parameter P1 (GPCS §11.6.2.1 Table 11-57):
     *                    {@link #LOAD_P1_MORE_BLOCKS} or {@link #LOAD_P1_LAST_BLOCK}.
     * @param blockNumber P2 – sequential block number starting from {@code 0x00}
     *                    (GPCS §11.6.2.2).
     * @param blockData   the portion of the Load File for this block; must not be {@code null}.
     * @return the LOAD {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code blockData} is {@code null}.
     */
    public static CommandApdu load(int p1, int blockNumber, byte[] blockData) {
        return new CommandApdu(0x80, INS_LOAD, p1, blockNumber, copyNonNull(blockData), 256);
    }

    /**
     * Builds a raw DELETE command per GPCS §11.2, Table 11-20.
     *
     * <p>Callers should prefer the higher-level helpers ({@link #deleteAid},
     * {@link #deleteAidAndRelated}) which build the correct TLV data field.
     *
     * @implSpec CLA={@code 0x80}, INS={@code 0xE4}.  Le={@code 0}.
     *
     * @param p1   reference control parameter P1 (GPCS §11.2.2.1 Table 11-21);
     *             see {@code DELETE_P1_*} constants.
     * @param p2   reference control parameter P2 (GPCS §11.2.2.2 Table 11-22);
     *             see {@code DELETE_P2_*} constants.
     * @param data TLV-encoded data field; must not be {@code null}.
     * @return the DELETE {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code data} is {@code null}.
     */
    public static CommandApdu delete(int p1, int p2, byte[] data) {
        return new CommandApdu(0x80, INS_DELETE, p1, p2, copyNonNull(data), 256);
    }

    /**
     * Builds a DELETE [card content] command that deletes only the specified object
     * (Application or Executable Load File) per GPCS §11.2.2.2 Table 11-22 and
     * §11.2.2.3.1 Table 11-23.
     *
     * <p>P2 bit b8 is set to {@code 0} ({@link #DELETE_P2_DELETE_OBJECT}), meaning only
     * the identified object is deleted.  Related Applications are <em>not</em> affected.
     * Use {@link #deleteAidAndRelated} to simultaneously delete an Executable Load File
     * and all its related Applications.
     *
     * @param aid AID of the Application or Executable Load File to delete (5–16 bytes);
     *            must not be {@code null}.
     * @return the DELETE {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code aid} is {@code null} or has invalid length.
     * @see #deleteAidAndRelated(byte[])
     */
    public static CommandApdu deleteAid(byte[] aid) {
        return delete(DELETE_P1_LAST_OR_ONLY, DELETE_P2_DELETE_OBJECT, buildDeleteAidData(aid));
    }

    /**
     * Builds a DELETE [card content] command that simultaneously deletes an Executable Load
     * File <em>and all its related Applications</em> per GPCS §11.2.2.2 Table 11-22 and
     * §11.2.2.3.1.
     *
     * <p>P2 bit b8 is set to {@code 1} ({@link #DELETE_P2_DELETE_OBJECT_AND_RELATED}).
     * Per GPCS §11.2.2.3.1: only the AID of the Executable Load File shall be provided
     * in the data field; individual Application AIDs must <em>not</em> be listed.
     *
     * @param aid AID of the Executable Load File (5–16 bytes); must not be {@code null}.
     * @return the DELETE {@link CommandApdu}.
     * @throws IllegalArgumentException if {@code aid} is {@code null} or has invalid length.
     * @see #deleteAid(byte[])
     */
    public static CommandApdu deleteAidAndRelated(byte[] aid) {
        return delete(DELETE_P1_LAST_OR_ONLY, DELETE_P2_DELETE_OBJECT_AND_RELATED, buildDeleteAidData(aid));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the common INSTALL [for install] / [for install and make selectable] data field
     * per GPCS §11.5.2.3.2 Table 11-43.
     *
     * @implSpec
     * Field encoding:
     * <ul>
     *   <li>Fields 1–4 (ELF AID, Module AID, Instance AID, Privileges) use
     *       <em>short (1-byte) LV</em> encoding.</li>
     *   <li>Fields 5–6 (Install Parameters, Install Token) use <em>BER-TLV</em> length
     *       encoding per GPCS §11.1.5 and §11.5.2.3 Table 11-43.</li>
     * </ul>
     * Install Token is always omitted (length {@code 0x00}) by this implementation.
     */
    private static CommandApdu installForApplication(
            int p1,
            byte[] loadFileAid,
            byte[] executableModuleAid,
            byte[] instanceAid,
            byte[] privileges,
            byte[] installParameters) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        // §11.5.2.3 Table 11-43: fields 1-4 use short (1-byte) LV encoding
        BerTlv.appendShortLv(data, GpUtil.requireAid(loadFileAid));
        BerTlv.appendShortLv(data, GpUtil.requireAid(executableModuleAid));
        BerTlv.appendShortLv(data, GpUtil.requireAid(instanceAid));
        BerTlv.appendShortLv(data, GpUtil.normalizePrivileges(privileges));
        // §11.5.2.3 Table 11-43: fields 5-6 use BER-TLV length encoding
        BerTlv.appendBerLv(data, GpUtil.normalizeInstallParameters(installParameters));
        BerTlv.appendBerLv(data, new byte[0]); // Install Token – omitted
        return install(p1, INSTALL_P2_NONE, data.toByteArray());
    }

    /**
     * Builds the DELETE command data field containing a single TLV-coded AID per
     * GPCS §11.2.2.3.1 Table 11-23: tag {@code 4F}, length, value.
     */
    private static byte[] buildDeleteAidData(byte[] aid) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] normalizedAid = GpUtil.requireAid(aid);
        // §11.2.2.3.1 Table 11-23: tag 4F identifies the AID of the object to delete
        data.write(TLV_TAG_AID);
        data.write(normalizedAid.length);
        data.write(normalizedAid, 0, normalizedAid.length);
        return data.toByteArray();
    }

    /** Defensive copy that rejects {@code null} input. */
    private static byte[] copyNonNull(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        return Arrays.copyOf(value, value.length);
    }

    /** Validates that {@code value} is non-null and exactly {@code length} bytes. */
    private static void requireLength(byte[] value, int length) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException("Value must be exactly " + length + " bytes");
        }
    }
}
