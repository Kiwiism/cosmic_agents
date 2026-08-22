package server.agents.integration.ollama;

import java.util.Map;

/** Independent high-level Director model settings with social Ollama defaults. */
public record DirectorLlmSettings(
        boolean enabled,
        String endpoint,
        String model,
        int timeoutMs,
        int numContext,
        int maxPredictTokens) {

    public DirectorLlmSettings {
        endpoint = text(endpoint).replaceAll("/+$", "");
        model = text(model);
        if (endpoint.isEmpty() || model.isEmpty() || timeoutMs < 250
                || numContext < 256 || maxPredictTokens < 1) {
            throw new IllegalArgumentException("valid Director LLM settings are required");
        }
    }

    public static DirectorLlmSettings runtime() {
        Map<String, String> env = System.getenv();
        return new DirectorLlmSettings(
                Boolean.parseBoolean(value(env, "DIRECTOR_LLM_ENABLED", "true")),
                value(env, "DIRECTOR_OLLAMA_ENDPOINT",
                        value(env, "SOCIAL_OLLAMA_ENDPOINT", "http://127.0.0.1:11434")),
                value(env, "DIRECTOR_OLLAMA_MODEL",
                        value(env, "SOCIAL_OLLAMA_MODEL", "qwen3.5:9b-q4_K_M")),
                integer(env, "DIRECTOR_OLLAMA_TIMEOUT_MS", 15_000),
                integer(env, "DIRECTOR_OLLAMA_NUM_CTX", 4096),
                integer(env, "DIRECTOR_OLLAMA_MAX_PREDICT", 160));
    }

    private static int integer(Map<String, String> env, String key, int fallback) {
        try { return Integer.parseInt(value(env, key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String value(Map<String, String> env, String key, String fallback) {
        String value = env.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
