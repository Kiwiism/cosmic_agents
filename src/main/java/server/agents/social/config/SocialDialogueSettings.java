package server.agents.social.config;

import server.agents.social.contracts.DialogueMode;

import java.util.Locale;
import java.util.Map;

/** Environment-owned settings for the optional external dialogue plugin. */
public record SocialDialogueSettings(
        DialogueMode mode,
        String endpoint,
        String model,
        int requestTimeoutMs,
        int maxResponseChars,
        int maxPredictTokens,
        int numContext,
        long circuitBreakerMs) {
    public SocialDialogueSettings {
        if (mode == null || endpoint == null || endpoint.isBlank() || model == null || model.isBlank()
                || requestTimeoutMs < 250 || maxResponseChars < 1 || maxResponseChars > 512
                || maxPredictTokens < 1 || numContext < 256 || circuitBreakerMs < 0) {
            throw new IllegalArgumentException("Valid social dialogue settings are required");
        }
        endpoint = endpoint.replaceAll("/+$", "");
        model = model.trim();
    }

    public static SocialDialogueSettings runtime() {
        return fromEnvironment(System.getenv());
    }

    static SocialDialogueSettings fromEnvironment(Map<String, String> environment) {
        DialogueMode mode;
        try {
            mode = DialogueMode.valueOf(value(environment, "SOCIAL_DIALOGUE_MODE", "DIALOGUE_ONLY")
                    .trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            mode = DialogueMode.DETERMINISTIC_ONLY;
        }
        return new SocialDialogueSettings(
                mode,
                value(environment, "SOCIAL_OLLAMA_ENDPOINT", "http://127.0.0.1:11434"),
                value(environment, "SOCIAL_OLLAMA_MODEL", "qwen3.5:9b-q4_K_M"),
                integer(environment, "SOCIAL_OLLAMA_TIMEOUT_MS", 6_000),
                integer(environment, "SOCIAL_DIALOGUE_MAX_CHARS", 180),
                integer(environment, "SOCIAL_OLLAMA_MAX_PREDICT", 80),
                integer(environment, "SOCIAL_OLLAMA_NUM_CTX", 4096),
                integer(environment, "SOCIAL_OLLAMA_CIRCUIT_MS", 30_000));
    }

    private static int integer(Map<String, String> environment, String key, int fallback) {
        try {
            return Integer.parseInt(value(environment, key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String value(Map<String, String> environment, String key, String fallback) {
        String property = System.getProperty(key.toLowerCase(Locale.ROOT).replace('_', '.'));
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
