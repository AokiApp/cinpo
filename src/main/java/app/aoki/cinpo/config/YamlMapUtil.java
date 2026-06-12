package app.aoki.cinpo.config;

import java.util.List;
import java.util.Map;

final class YamlMapUtil {

    private YamlMapUtil() {
    }

    static String getString(Map<String, Object> data, String key, Object source) {
        Object value = requireValue(data, key, source);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(
                    "Field '" + key + "' must be a string in " + source
                            + " (found: " + value.getClass().getSimpleName() + ")");
        }
        return stringValue;
    }

    static int getInt(Map<String, Object> data, String key, Object source) {
        Object value = requireValue(data, key, source);
        if (!(value instanceof Number numberValue)) {
            throw new IllegalArgumentException(
                    "Field '" + key + "' must be a number in " + source
                            + " (found: " + value.getClass().getSimpleName() + ")");
        }
        return numberValue.intValue();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getMap(Map<String, Object> data, String key, Object source) {
        Object value = requireValue(data, key, source);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "Field '" + key + "' must be a map in " + source
                            + " (found: " + value.getClass().getSimpleName() + ")");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> getMapList(Map<String, Object> data, String key, Object source) {
        Object value = requireValue(data, key, source);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(
                    "Field '" + key + "' must be a list in " + source
                            + " (found: " + value.getClass().getSimpleName() + ")");
        }
        return (List<Map<String, Object>>) value;
    }

    private static Object requireValue(Map<String, Object> data, String key, Object source) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field '" + key + "' in " + source);
        }
        return value;
    }
}
