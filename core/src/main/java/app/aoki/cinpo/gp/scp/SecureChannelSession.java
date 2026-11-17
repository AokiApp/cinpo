package app.aoki.cinpo.gp.scp;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import java.util.Objects;

/**
 * Wraps an {@link ApduChannel} to provide GlobalPlatform Secure Channel messaging.
 *
 * <p>A Secure Channel Session passes through the three sequential phases defined in
 * [GPCS] §10.1:
 * <ol>
 *   <li><b>Initiation</b> – the on-card Security Domain and the off-card entity
 *       exchange information to enable cryptographic functions and the off-card entity
 *       is authenticated by the card. Driven by {@link #authenticate()}.</li>
 *   <li><b>Operation</b> – the two parties exchange data within the cryptographic
 *       protection of the session. All APDUs sent via {@link #transmit} are wrapped
 *       with secure messaging before being forwarded to the card and unwrapped on
 *       return.</li>
 *   <li><b>Termination</b> – the session ends; all session data, ICVs, and session
 *       keys are erased ([GPCS] §10.2.3). Driven by {@link #close()}.</li>
 * </ol>
 *
 * <h2>Session State Machine</h2>
 * <pre>
 *   CREATED ──authenticate()──► AUTHENTICATED ──close()──► CLOSED
 *      │                              │
 *      └──(any exception)──► ABORTED ─┘
 * </pre>
 * <ul>
 *   <li>{@code CREATED} – initial state; {@link #authenticate()} has not yet succeeded.</li>
 *   <li>{@code AUTHENTICATED} – {@link #authenticate()} succeeded; {@link #transmit}
 *       may be called.</li>
 *   <li>{@code ABORTED} – a {@link RuntimeException} was thrown during
 *       {@link #authenticate()} or {@link #transmit}; the session must be discarded and
 *       a new one created.</li>
 *   <li>{@code CLOSED} – {@link #close()} was called; all resources are released.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All public methods are synchronized on an internal monitor. Concurrent access from
 * multiple threads is safe, but calls are serialized — there is no parallelism within
 * a single session.
 *
 * <p>Instances are created via {@link #create(ApduChannel, SecureChannelProfile)}, not
 * directly.
 *
 * @see SecureChannelProfile
 */
public final class SecureChannelSession implements AutoCloseable {

    private final ApduChannel channel;
    private final InternalSecureChannelProtocol protocol;
    private final Object monitor = new Object();

    private SessionState state;

    private SecureChannelSession(ApduChannel channel, InternalSecureChannelProtocol protocol) {
        this.channel = Objects.requireNonNull(channel);
        this.protocol = Objects.requireNonNull(protocol);
        this.state = SessionState.CREATED;
    }

    /**
     * Creates a session for the SCP variant declared by {@code profile}.
     *
     * @param channel the underlying APDU transport; must not be null
     * @param profile configuration profile produced by {@link SecureChannelProfile#scp02}
     *                or {@link SecureChannelProfile#scp03};
     *                must not be null
     * @return a new {@code SecureChannelSession} in the {@code CREATED} state
     */
    public static SecureChannelSession create(ApduChannel channel, SecureChannelProfile profile) {
        Objects.requireNonNull(profile);
        return switch (profile.scp()) {
            case SCP02 -> new SecureChannelSession(channel, new Scp02Protocol(profile));
            case SCP03 -> new SecureChannelSession(channel, new Scp03Protocol(profile));
        };
    }

    /**
     * Performs explicit Secure Channel initiation ([GPCS] §10.2.1).
     *
     * <p><strong>Precondition:</strong> the card's Security Domain identified by
     * {@link SecureChannelProfile#securityDomainAid()} must already be selected before calling
     * this method. Callers are responsible for issuing the plain SELECT command.
     *
     * <p>The following sequence is executed:
     * <ol>
     *   <li><b>INITIALIZE UPDATE</b> ([Amd D] §7.1.1) – transmits the 8-byte host challenge
     *       to the card and receives key diversification data, the card challenge, and the
     *       card cryptogram.  Session keys are derived from the static key set.</li>
     *   <li><b>EXTERNAL AUTHENTICATE</b> ([Amd D] §7.1.2) – sends the host cryptogram and
     *       establishes the requested security level (P1).  On success the card transitions
     *       into the Operation phase and MAC chaining begins.</li>
     * </ol>
     *
     * <p>If this method returns normally, the session moves to the {@code AUTHENTICATED}
     * state and {@link #transmit} may be called.  If a {@link RuntimeException} is thrown
     * the session moves to {@code ABORTED} and must be discarded.
     *
     * <p>Calling this method on an already-{@code AUTHENTICATED} session is a no-op.
     *
     * @throws IllegalStateException if the session is {@code ABORTED} or {@code CLOSED}
     * @throws RuntimeException      (or subclass) on any protocol or transport failure;
     *                               the session is moved to {@code ABORTED}
     */
    public void authenticate() {
        synchronized (monitor) {
            ensureNotClosed();
            if (state == SessionState.AUTHENTICATED) {
                return;
            }
            if (state == SessionState.ABORTED) {
                throw new IllegalStateException("Secure channel session is aborted and must be recreated");
            }

            try {
                protocol.authenticate(channel);
                state = SessionState.AUTHENTICATED;
            } catch (RuntimeException e) {
                state = SessionState.ABORTED;
                throw e;
            }
        }
    }

    /**
     * Wraps {@code capdu} with secure messaging, transmits it over the underlying channel,
     * and unwraps the response.
     *
     * <p>The exact wrapping applied depends on the security level negotiated during
     * {@link #authenticate()}: C-MAC integrity, optional C-DECRYPTION, optional R-MAC, and
     * optional R-ENCRYPTION per [Amd D] §7.1.2.3 Table 7-6.
     *
     * <p><b>Note:</b> any {@link RuntimeException} thrown — whether from cryptographic
     * processing, transport errors, or unexpected response status words — will move the
     * session to the {@code ABORTED} state. The session cannot be recovered after abort;
     * a new {@code SecureChannelSession} must be created.
     *
     * @param capdu the plaintext command APDU to protect and send; must not be null
     * @return the unwrapped (plaintext) response APDU from the card
     * @throws IllegalStateException if the session is not in the {@code AUTHENTICATED} state
     * @throws RuntimeException      (or subclass) on any protocol or transport failure;
     *                               the session is moved to {@code ABORTED}
     */
    public ResponseApdu transmit(CommandApdu capdu) {
        synchronized (monitor) {
            ensureAuthenticated();
            try {
                return protocol.transmit(channel, capdu);
            } catch (RuntimeException e) {
                state = SessionState.ABORTED;
                throw e;
            }
        }
    }

    /**
     * Returns {@code true} if the session is in the {@code AUTHENTICATED} state, i.e.
     * {@link #authenticate()} has completed successfully and {@link #close()} has not yet
     * been called.
     *
     * @return {@code true} when {@link #transmit} may be called
     */
    public boolean isAuthenticated() {
        synchronized (monitor) {
            return state == SessionState.AUTHENTICATED;
        }
    }

    /**
     * Returns {@code true} if the session is in the {@code ABORTED} state.
     *
     * <p>A session becomes aborted when a {@link RuntimeException} is thrown during
     * {@link #authenticate()} or {@link #transmit}. An aborted session cannot be used
     * further; a new {@code SecureChannelSession} must be created.
     *
     * @return {@code true} when the session has been aborted due to an error
     */
    public boolean isAborted() {
        synchronized (monitor) {
            return state == SessionState.ABORTED;
        }
    }

    /**
     * Returns {@code true} if the session is in the {@code CLOSED} state, i.e.
     * {@link #close()} has been called.
     *
     * @return {@code true} when the session has been closed
     */
    public boolean isClosed() {
        synchronized (monitor) {
            return state == SessionState.CLOSED;
        }
    }

    /**
     * Terminates the Secure Channel Session and releases all resources.
     *
     * <p>Per [GPCS] §10.2.3, "Secure Channel termination causes all session data to be
     * reset and all ICVs and session keys to be erased." After this method returns, the
     * session moves to the {@code CLOSED} state and further method calls will throw
     * {@link IllegalStateException}.
     *
     * <p>This method is idempotent: calling it on an already-{@code CLOSED} session is a
     * no-op.  It is safe to call on sessions in any state (including {@code CREATED} and
     * {@code ABORTED}) — resources are always released.
     *
     * <p>This method is invoked automatically when the session is used in a
     * try-with-resources statement.
     */
    @Override
    public void close() {
        synchronized (monitor) {
            if (state == SessionState.CLOSED) {
                return;
            }
            try {
                protocol.close();
            } finally {
                state = SessionState.CLOSED;
                // NOTE: channel lifecycle is the caller's responsibility.
                // SecureChannelSession does not own the ApduChannel it was given,
                // so it must not close it here.
            }
        }
    }

    private void ensureAuthenticated() {
        ensureNotClosed();
        if (state != SessionState.AUTHENTICATED) {
            throw new IllegalStateException("Secure channel session is not authenticated");
        }
    }

    private void ensureNotClosed() {
        if (state == SessionState.CLOSED) {
            throw new IllegalStateException("Secure channel session is already closed");
        }
    }

    private enum SessionState {
        CREATED,
        AUTHENTICATED,
        ABORTED,
        CLOSED
    }
}
