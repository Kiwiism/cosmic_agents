package server.bots.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Minimal Ollama HTTP client. Uses /api/generate with stream=false.
 * Hard timeout enforced via HttpClient + per-request timeout. Failures
 * are swallowed and returned as Optional.empty so callers never crash
 * the game loop on Ollama issues.
 */
public final class OllamaClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(5000))
            .build();

    private OllamaClient() {}

    public static Optional<String> generate(String prompt, String system) {
        return send(prompt, system, BotLlmConfig.maxPredictTokens, BotLlmConfig.requestTimeoutMs);
    }

    /** Variant for non-chat calls (e.g. memory summarization) where we want a bigger
     *  token budget and a proportionally longer timeout. */
    public static Optional<String> generateLong(String prompt, String system, int numPredict) {
        // Rough scale: small CPU produces ~10 tok/s, so allow numPredict*200ms + base.
        int timeoutMs = Math.max(BotLlmConfig.requestTimeoutMs, 5000 + numPredict * 200);
        return send(prompt, system, numPredict, timeoutMs);
    }

    private static Optional<String> send(String prompt, String system, int numPredict, int timeoutMs) {
        String body = buildBody(prompt, system, numPredict);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(BotLlmConfig.endpoint + "/api/generate"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (Exception e) {
            log.warn("ollama: request build failed: {}", e.toString());
            return Optional.empty();
        }
        try {
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("ollama: HTTP {} body: {}", resp.statusCode(), abbrev(resp.body(), 200));
                return Optional.empty();
            }
            String text = extractResponseField(resp.body());
            if (text == null || text.isBlank()) return Optional.empty();
            return Optional.of(text.trim());
        } catch (java.net.http.HttpTimeoutException te) {
            log.info("ollama: timeout after {}ms", timeoutMs);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("ollama: send failed: {}", e.toString());
            return Optional.empty();
        }
    }

    private static String buildBody(String prompt, String system, int numPredict) {
        StringBuilder sb = new StringBuilder(384);
        sb.append('{');
        sb.append("\"model\":\"").append(jsonEscape(BotLlmConfig.model)).append("\",");
        sb.append("\"stream\":false,");
        if (BotLlmConfig.keepAlive != null && !BotLlmConfig.keepAlive.isBlank()) {
            sb.append("\"keep_alive\":\"").append(jsonEscape(BotLlmConfig.keepAlive)).append("\",");
        }
        if (system != null && !system.isEmpty()) {
            sb.append("\"system\":\"").append(jsonEscape(system)).append("\",");
        }
        sb.append("\"prompt\":\"").append(jsonEscape(prompt)).append("\",");
        if (BotLlmConfig.disableThinking) {
            sb.append("\"think\":false,");
        }
        sb.append("\"options\":{")
                .append("\"num_predict\":").append(numPredict)
                .append(",\"temperature\":").append(BotLlmConfig.temperature)
                .append(",\"top_p\":").append(BotLlmConfig.topP)
                .append(",\"top_k\":").append(BotLlmConfig.topK)
                .append(",\"min_p\":").append(BotLlmConfig.minP)
                .append(",\"presence_penalty\":").append(BotLlmConfig.presencePenalty)
                .append(",\"repeat_penalty\":").append(BotLlmConfig.repeatPenalty);
        if (BotLlmConfig.numCtx > 0) {
            sb.append(",\"num_ctx\":").append(BotLlmConfig.numCtx);
        }
        if (BotLlmConfig.numThreads > 0) {
            sb.append(",\"num_thread\":").append(BotLlmConfig.numThreads);
        }
        sb.append("}}");
        return sb.toString();
    }

    /**
     * Minimal JSON parsing — extract the "response" string from Ollama's
     * non-streaming reply without pulling in a JSON dependency. The reply
     * shape is: {"model":...,"response":"text",...}
     */
    static String extractResponseField(String json) {
        if (json == null) return null;
        int key = json.indexOf("\"response\"");
        if (key < 0) return null;
        int colon = json.indexOf(':', key);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        i++;
        StringBuilder out = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            try {
                                out.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                            } catch (NumberFormatException ignored) {}
                            i += 4;
                        }
                    }
                    default -> out.append(n);
                }
                i += 2;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
                i++;
            }
        }
        return null;
    }

    static String jsonEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static String abbrev(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
