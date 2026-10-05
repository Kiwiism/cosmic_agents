package server.agents.integration.typesafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP adapter for {@code POST /v1/systemone}. One immediate retry on 529/5xx and on connection
 * failures; 429 is not retried (the caller's circuit breaker and deterministic fallback absorb
 * sustained rate limiting without a blocking wait), and 401/403/422 and malformed bodies fail
 * at once.
 */
public final class JevHttpTransport implements JevTransport {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;

    public JevHttpTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    public JevHttpTransport(HttpClient http) {
        this.http = http;
    }

    @Override
    public JevResponse send(JevRequest request, TypeSafeSettings settings) throws JevTransportException {
        String body;
        try {
            body = JSON.writeValueAsString(request.toJson(settings.model()));
        } catch (IOException failure) {
            throw new JevTransportException("System One request could not be serialised", failure, false);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(settings.endpoint()))
                .timeout(Duration.ofMillis(settings.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return exchange(httpRequest);
        } catch (JevTransportException first) {
            if (!first.retryable() || first.statusCode() == 429) {
                throw first;
            }
            return exchange(httpRequest);
        }
    }

    private JevResponse exchange(HttpRequest httpRequest) throws JevTransportException {
        long startedAt = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new JevTransportException("System One request failed: " + failure.getMessage(), failure, true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JevTransportException("System One request interrupted", interrupted, false);
        }
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        int status = response.statusCode();
        if (status != 200) {
            boolean retryable = status == 429 || status == 529 || (status >= 500 && status < 600);
            throw new JevTransportException("System One returned HTTP " + status, status, retryable);
        }
        try {
            return parse(JSON.readTree(response.body()), latencyMs);
        } catch (IOException | RuntimeException failure) {
            throw new JevTransportException("System One response could not be parsed", failure, false);
        }
    }

    static JevResponse parse(JsonNode root, long latencyMs) {
        if (root == null || !root.isObject() || !root.path("answers").isObject()) {
            throw new IllegalArgumentException("System One response must contain an answers object");
        }
        Map<String, JevAnswer> answers = new LinkedHashMap<>();
        JsonNode answerNodes = root.path("answers");
        for (Iterator<Map.Entry<String, JsonNode>> it = answerNodes.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            answers.put(entry.getKey(), parseAnswer(entry.getValue()));
        }
        JsonNode usage = root.path("usage");
        return new JevResponse(
                root.path("model").asText(""),
                answers,
                usage.path("input_tokens").asLong(0L),
                usage.path("output_tokens").asLong(0L),
                latencyMs);
    }

    private static JevAnswer parseAnswer(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("System One answer must be an object");
        }
        switch (node.path("type").asText()) {
            case "choice" -> {
                if (!node.path("choice").isTextual() || node.path("choice").asText().isBlank()) {
                    throw new IllegalArgumentException("Choice answer must contain a choice");
                }
            }
            case "noul" -> requireNumber(node.get("noul"), true);
            case "score" -> requireNumber(node.get("score"), false);
            default -> throw new IllegalArgumentException("Unknown System One answer type");
        }
        if (node.has("confidence")) {
            requireNumber(node.get("confidence"), true);
        }
        if (node.has("probabilities") && !node.get("probabilities").isObject()) {
            throw new IllegalArgumentException("Answer probabilities must be an object");
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = node.path("probabilities").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            requireNumber(entry.getValue(), true);
            probabilities.put(entry.getKey(), entry.getValue().doubleValue());
        }
        Map<String, String> legend = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = node.path("legend").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            legend.put(entry.getKey(), entry.getValue().asText(""));
        }
        return new JevAnswer(
                node.path("type").asText(""),
                node.hasNonNull("choice") ? node.get("choice").asText() : null,
                probabilities,
                node.hasNonNull("noul") ? node.get("noul").asDouble() : null,
                node.hasNonNull("score") ? node.get("score").asDouble() : null,
                legend,
                node.hasNonNull("confidence") ? node.get("confidence").asDouble() : null);
    }

    private static void requireNumber(JsonNode node, boolean probability) {
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())
                || (probability && (node.doubleValue() < 0.0d || node.doubleValue() > 1.0d))) {
            throw new IllegalArgumentException("Invalid numeric System One answer");
        }
    }
}
