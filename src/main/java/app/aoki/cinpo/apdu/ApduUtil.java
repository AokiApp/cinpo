package app.aoki.cinpo.apdu;

public final class ApduUtil {

    private ApduUtil() {
    }

    public static ResponseApdu assertSwOk(ResponseApdu response) {
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("APDU failed with SW=" + String.format("%04X", response.sw()));
        }
        return response;
    }
}
