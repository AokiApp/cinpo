package app.aoki.cinpo.config;

import java.util.List;
import java.util.Objects;

/**
 * PC/SC reader selection configuration.
 *
 * <p>If {@code readerName} is set, CINPO uses that reader only and does not fall back
 * to other readers. If {@code readerName} is absent, CINPO automatically searches for
 * a reader with a card present, skipping names listed in {@code excludedReaderNames},
 * and falls back to the next matching reader when connection fails.
 */
public record PcscConfig(
        String readerName,
        List<String> excludedReaderNames
) {
    public PcscConfig {
        excludedReaderNames = List.copyOf(Objects.requireNonNullElse(excludedReaderNames, List.of()));
        readerName = normalize(readerName);
        for (String excludedReaderName : excludedReaderNames) {
            if (excludedReaderName == null || excludedReaderName.isBlank()) {
                throw new IllegalArgumentException("pcsc.excludedReaders must not contain null or blank entries");
            }
        }
        if (readerName != null && !excludedReaderNames.isEmpty()) {
            throw new IllegalArgumentException("pcsc.reader and pcsc.excludedReaders cannot be used together");
        }
    }

    public static PcscConfig automatic() {
        return new PcscConfig(null, List.of());
    }

    public boolean hasSpecificReader() {
        return readerName != null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
