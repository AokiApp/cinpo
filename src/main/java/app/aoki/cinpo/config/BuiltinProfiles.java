package app.aoki.cinpo.config;

import app.aoki.cinpo.util.Util;

/**
 * Built-in configuration profiles for common development scenarios.
 *
 * <p>These profiles are embedded in the framework and available without external
 * YAML configuration. They use well-known test keys and are suitable for simulator
 * environments but <b>must never be used in production</b>.
 *
 * <h2>Available Profiles</h2>
 * <ul>
 *   <li><b>jcdksim</b> - Default profile for jcresim simulator with SCP03 test keys</li>
 * </ul>
 *
 * @see ProfileLoader
 */
public final class BuiltinProfiles {

    /**
     * Test encryption key: 32 bytes of 0x11.
     * <b>WARNING:</b> This is a well-known test key. Never use in production.
     */
    private static final byte[] TEST_ENC_KEY = Util.parseHex(
            "1111111111111111111111111111111111111111111111111111111111111111"
    );

    /**
     * Test MAC key: 32 bytes of 0x22.
     * <b>WARNING:</b> This is a well-known test key. Never use in production.
     */
    private static final byte[] TEST_MAC_KEY = Util.parseHex(
            "2222222222222222222222222222222222222222222222222222222222222222"
    );

    /**
     * Test DEK key: 32 bytes of 0x33.
     * <b>WARNING:</b> This is a well-known test key. Never use in production.
     */
    private static final byte[] TEST_DEK_KEY = Util.parseHex(
            "3333333333333333333333333333333333333333333333333333333333333333"
    );

    /**
     * Default GlobalPlatform ISD AID for Oracle JCDK simulator.
     * This is the standard ISD AID: A000000151000000
     */
    private static final byte[] JCDK_ISD_AID = Util.parseHex("A000000151000000");

    /**
     * Default profile for jcresim simulator with SCP03.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Runtime: jcresim (Oracle JCDK simulator)</li>
     *   <li>Protocol: SCP03 (AES-based)</li>
     *   <li>ISD AID: A000000151000000 (default JCDK ISD)</li>
     *   <li>Key Version Number: 16 (0x10)</li>
     *   <li>Key Identifier: 0 (0x00)</li>
     *   <li>Security Level: 1 (C-MAC only)</li>
     *   <li>Keys: Test keys (111...111, 222...222, 333...333)</li>
     * </ul>
     *
     * <p><b>This profile is for development only.</b> The test keys are publicly known
     * and must never be used with real cards.
     */
    public static final Profile JCDKSIM = new Profile(
            "jcdksim",
            "jcresim",
            new ScpConfig(
                    "scp03",
                    JCDK_ISD_AID,
                    16,  // keyVersionNumber (0x10)
                    0,   // keyIdentifier (0x00 - required for SCP03)
                    1,   // securityLevel (0x01 - C-MAC only)
                    TEST_ENC_KEY,
                    TEST_MAC_KEY,
                    TEST_DEK_KEY
            )
    );

    private BuiltinProfiles() {
        // Utility class - no instantiation
    }
}
