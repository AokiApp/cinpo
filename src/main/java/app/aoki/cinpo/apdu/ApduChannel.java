package app.aoki.cinpo.apdu;

/**
 * Minimal APDU exchange boundary between controllers and a card runtime.
 */
public interface ApduChannel extends AutoCloseable {

    /**
     * Transmit a command APDU and return the response APDU.
     *
     * @param capdu command APDU value object
     * @return response APDU including status words
     */
    ResponseApdu transmit(CommandApdu capdu);

    /**
     * Close any runtime-side resources associated with this channel.
     */
    @Override
    default void close() {
    }
}
