package app.aoki.cinpo.runtime.pcsc;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.PcscConfig;
import java.util.List;
import java.util.Objects;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * PC/SC adapter that connects to physical smart card readers via javax.smartcardio.
 */
public final class PcscAdapter {

    private static final String CONNECT_PROTOCOL = "*";

    private final PcscConfig config;

    public PcscAdapter() {
        this(PcscConfig.automatic());
    }

    public PcscAdapter(PcscConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Create an APDU channel backed by PC/SC.
     *
     * @return an APDU channel implementation
     */
    public ApduChannel createChannel() {
        return new PcscChannel(config);
    }

    /**
     * APDU channel implementation backed by PC/SC.
     */
    private static final class PcscChannel implements ApduChannel {

        private final Object monitor = new Object();
        private final PcscConfig config;
        private Card card;
        private CardChannel basicChannel;

        PcscChannel(PcscConfig config) {
            this.config = Objects.requireNonNull(config);
        }

        @Override
        public ResponseApdu transmit(CommandApdu capdu) {
            Objects.requireNonNull(capdu);

            synchronized (monitor) {
                try {
                    CardChannel channel = ensureBasicChannel();
                    javax.smartcardio.ResponseAPDU rapdu = channel.transmit(new CommandAPDU(capdu.toBytes()));
                    return ResponseApdu.fromBytes(rapdu.getBytes());
                } catch (CardException e) {
                    invalidateConnection();
                    throw new IllegalStateException("Failed to transmit APDU via PC/SC", e);
                } catch (IllegalStateException e) {
                    invalidateConnection();
                    throw e;
                } catch (RuntimeException e) {
                    invalidateConnection();
                    throw new IllegalStateException("PC/SC transport failed during APDU exchange", e);
                }
            }
        }

        @Override
        public void close() {
            synchronized (monitor) {
                try {
                    if (card != null) {
                        card.disconnect(false);
                    }
                } catch (CardException e) {
                    throw new IllegalStateException("Failed to close the PC/SC session", e);
                } finally {
                    invalidateConnection();
                }
            }
        }

        private CardChannel ensureBasicChannel() throws CardException {
            if (basicChannel != null) {
                return basicChannel;
            }

            card = connectCard();
            basicChannel = card.getBasicChannel();
            return basicChannel;
        }

        private Card connectCard() throws CardException {
            TerminalFactory terminalFactory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = terminalFactory.terminals().list();
            if (config.hasSpecificReader()) {
                return connectSpecificReader(terminals);
            }
            return connectAutomatically(terminals);
        }

        private Card connectSpecificReader(List<CardTerminal> terminals) throws CardException {
            CardTerminal terminal = terminals.stream()
                    .filter(candidate -> config.readerName().equals(candidate.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("PC/SC card reader not found: " + config.readerName()));
            return terminal.connect(CONNECT_PROTOCOL);
        }

        private Card connectAutomatically(List<CardTerminal> terminals) throws CardException {
            List<CardTerminal> candidates = terminals.stream()
                    .filter(this::isAutomaticCandidate)
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No PC/SC card reader with a card present was found");
            }

            CardException lastFailure = null;
            for (CardTerminal candidate : candidates) {
                try {
                    return candidate.connect(CONNECT_PROTOCOL);
                } catch (CardException e) {
                    lastFailure = e;
                }
            }
            throw new CardException("Failed to connect to any automatically selected PC/SC reader", lastFailure);
        }

        private boolean isAutomaticCandidate(CardTerminal terminal) {
            if (config.excludedReaderNames().contains(terminal.getName())) {
                return false;
            }
            try {
                return terminal.isCardPresent();
            } catch (CardException e) {
                return false;
            }
        }

        private void invalidateConnection() {
            basicChannel = null;
            card = null;
        }
    }
}
