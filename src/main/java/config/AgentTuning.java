package config;

import java.util.Map;

/**
 * Strict typed access to Agent algorithm and capability tuning values.
 *
 * <p>Keys use the declaring class plus constant name. Missing or malformed
 * values fail startup at first use rather than silently applying a Java
 * fallback. Content IDs, protocol constants, deterministic random domains,
 * and schema versions are deliberately not tuning keys.</p>
 */
public final class AgentTuning {
    private AgentTuning() {
    }

    public static int intValue(String key) {
        try {
            return Integer.parseInt(required(key));
        } catch (NumberFormatException failure) {
            throw invalid(key, "integer", failure);
        }
    }

    public static long longValue(String key) {
        try {
            return Long.parseLong(required(key));
        } catch (NumberFormatException failure) {
            throw invalid(key, "long", failure);
        }
    }

    public static double doubleValue(String key) {
        try {
            return Double.parseDouble(required(key));
        } catch (NumberFormatException failure) {
            throw invalid(key, "double", failure);
        }
    }

    public static float floatValue(String key) {
        try {
            return Float.parseFloat(required(key));
        } catch (NumberFormatException failure) {
            throw invalid(key, "float", failure);
        }
    }

    public static boolean booleanValue(String key) {
        String value = required(key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalStateException(
                "Agent tuning key '" + key + "' must be a valid boolean");
    }

    public static Map<String, String> snapshot() {
        return Map.copyOf(AgentYamlConfig.config.tuning);
    }

    private static String required(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Agent tuning key is required");
        }
        String value = AgentYamlConfig.config.tuning.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing Agent tuning key '" + key + "' in "
                            + AgentYamlConfig.CONFIG_FILE_NAME);
        }
        return value.trim();
    }

    private static IllegalStateException invalid(
            String key, String expectedType, NumberFormatException cause) {
        return new IllegalStateException(
                "Agent tuning key '" + key + "' must be a valid " + expectedType, cause);
    }
}
