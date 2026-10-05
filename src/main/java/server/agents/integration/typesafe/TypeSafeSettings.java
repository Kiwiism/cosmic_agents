package server.agents.integration.typesafe;

import java.util.Map;

/**
 * Deployment settings for the TypeSafe System One API (Jev).
 *
 * <p>The API key is a secret and is therefore read from the environment only, never from a
 * committed configuration file. Endpoint, model, and timeout can be overridden the same way.
 * Feature switches (which judgments are enabled, and in which mode) live in
 * {@code agent-engine.yaml} like every other Agent deployment setting.
 */
public record TypeSafeSettings(String apiKey, String endpoint, String model, int timeoutMs) {
    public static final String API_KEY_ENV = "TYPESAFE_API_KEY";
    public static final String ENDPOINT_ENV = "TYPESAFE_ENDPOINT";
    public static final String MODEL_ENV = "TYPESAFE_MODEL";
    public static final String TIMEOUT_ENV = "TYPESAFE_TIMEOUT_MS";
    public static final String DEFAULT_ENDPOINT = "https://api.typesafe.ai/v1/systemone";
    public static final String DEFAULT_MODEL = "jev-latest";

    public TypeSafeSettings {
        apiKey = text(apiKey);
        endpoint = text(endpoint).replaceAll("/+$", "");
        model = text(model);
        if (endpoint.isEmpty() || model.isEmpty() || timeoutMs < 250) {
            throw new IllegalArgumentException("valid TypeSafe settings are required");
        }
    }

    /** True when an API key is present; without one every judgment is silently unavailable. */
    public boolean configured() {
        return !apiKey.isEmpty();
    }

    public static TypeSafeSettings runtime() {
        return fromEnvironment(System.getenv());
    }

    public static TypeSafeSettings fromEnvironment(Map<String, String> env) {
        return new TypeSafeSettings(
                value(env, API_KEY_ENV, ""),
                value(env, ENDPOINT_ENV, DEFAULT_ENDPOINT),
                value(env, MODEL_ENV, DEFAULT_MODEL),
                integer(env, TIMEOUT_ENV, 1_500));
    }

    /** Settings with no key: every request is refused locally without touching the network. */
    public static TypeSafeSettings disabled() {
        return new TypeSafeSettings("", DEFAULT_ENDPOINT, DEFAULT_MODEL, 1_500);
    }

    private static int integer(Map<String, String> env, String key, int fallback) {
        try {
            return Integer.parseInt(value(env, key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String value(Map<String, String> env, String key, String fallback) {
        String value = env.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return "TypeSafeSettings[endpoint=" + endpoint + ", model=" + model
                + ", timeoutMs=" + timeoutMs + ", apiKey=" + (configured() ? "<set>" : "<missing>") + "]";
    }
}
