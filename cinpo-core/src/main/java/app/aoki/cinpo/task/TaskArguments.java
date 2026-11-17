package app.aoki.cinpo.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Raw task-specific runtime arguments passed after the CLI {@code --} delimiter.
 *
 * <p>CINPO itself does not interpret these arguments. They are forwarded verbatim to
 * host-side tasks so template projects can define their own selectors such as
 * {@code --record 123} or {@code --db path/to/data.db}.
 */
public record TaskArguments(List<String> raw) {

    private static final TaskArguments EMPTY = new TaskArguments(List.of());

    public TaskArguments {
        Objects.requireNonNull(raw);
        raw = List.copyOf(raw);
    }

    public static TaskArguments empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    public boolean hasFlag(String optionName) {
        String normalized = normalizeOptionName(optionName);
        for (String token : raw) {
            if (token.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public Optional<String> firstValue(String optionName) {
        List<String> values = values(optionName);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.get(0));
    }

    public List<String> values(String optionName) {
        String normalized = normalizeOptionName(optionName);
        String inlinePrefix = normalized + "=";
        List<String> values = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++) {
            String token = raw.get(i);
            if (token.equals(normalized)) {
                if (i + 1 >= raw.size()) {
                    throw new IllegalArgumentException("Missing value for task argument: " + normalized);
                }
                String value = raw.get(i + 1);
                if (looksLikeLongOption(value)) {
                    throw new IllegalArgumentException("Missing value for task argument: " + normalized);
                }
                values.add(value);
                i++;
                continue;
            }
            if (token.startsWith(inlinePrefix)) {
                values.add(token.substring(inlinePrefix.length()));
            }
        }

        return List.copyOf(values);
    }

    private static String normalizeOptionName(String optionName) {
        Objects.requireNonNull(optionName);
        String trimmed = optionName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Task argument name must not be blank");
        }
        if (trimmed.startsWith("--")) {
            return trimmed;
        }
        return "--" + trimmed;
    }

    private static boolean looksLikeLongOption(String token) {
        return token.startsWith("--");
    }
}
