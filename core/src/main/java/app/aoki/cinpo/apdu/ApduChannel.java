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
     * Reset the underlying card/runtime session while keeping the Java-side channel object usable.
     *
     * <p>Implementations should drop transport/session state so the next
     * {@nlink transmit} re-establishes a fresh card context. Persistent card data
     * must not be erased by this operation.</p>
     */
    void reset();

    /**
     * Close any runtime-side resources associated with this channel.
     */
    @Override
    void close();
}
