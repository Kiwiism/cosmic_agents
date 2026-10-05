package server.agents.integration.typesafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevHttpTransportTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ANSWERS = """
            {"model":"jev-1.13.0","answers":{
              "intent":{"type":"choice","choice":"grind","probabilities":{"grind":0.82,"patrol":0.11,"chat_not_a_command":0.07},"confidence":0.79},
              "addressed":{"type":"noul","noul":0.91},
              "fit":{"type":"score","score":3.4,"legend":{"1":"poor fit","2":"partial","3":"good fit","4":"ideal fit"},"probabilities":{"3":0.6,"4":0.4},"confidence":0.7}
            },"usage":{"input_tokens":812,"output_tokens":0}}
            """;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsContractShapedBodyWithBearerAuthAndParsesTypedAnswers() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        start(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, ANSWERS);
        });

        JevResponse response = new JevHttpTransport().send(request(), settings());

        assertEquals("Bearer test-key", auth.get());
        JsonNode sent = JSON.readTree(body.get());
        assertEquals("jev-latest", sent.path("model").asText());
        assertEquals("farm around here pls", sent.path("state").path("message").asText());
        assertEquals("choice", sent.path("questions").path("intent").path("type").asText());
        assertEquals("noul", sent.path("questions").path("addressed").path("type").asText());
        assertEquals("score", sent.path("questions").path("fit").path("type").asText());
        assertEquals(2, sent.path("questions").path("fit").path("criteria").size());

        assertEquals("jev-1.13.0", response.model());
        assertEquals(812L, response.inputTokens());
        JevAnswer intent = response.answer("intent").orElseThrow();
        assertTrue(intent.isChoice());
        assertEquals("grind", intent.choice());
        assertEquals(0.82d, intent.probabilityOf("grind"), 1e-9);
        assertEquals(0.79d, intent.confidenceOrZero(), 1e-9);
        assertEquals(0.91d, response.yesProbability("addressed"), 1e-9);
        JevAnswer fit = response.answer("fit").orElseThrow();
        assertEquals(3.4d, fit.scoreOrZero(), 1e-9);
        assertEquals("good fit", fit.legend().get("3"));
        assertEquals("3", fit.mostLikelyOption().orElseThrow());
    }

    @Test
    void retriesOnceImmediatelyAfterOverload() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 529, "{\"error\":\"overloaded\"}");
            } else {
                respond(exchange, 200, ANSWERS);
            }
        });

        JevResponse response = new JevHttpTransport().send(request(), settings());

        assertEquals(2, calls.get());
        assertEquals("grind", response.answer("intent").orElseThrow().choice());
    }

    @Test
    void doesNotRetryRateLimitsButReportsThemRetryable() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 429, "{\"error\":\"slow down\"}");
        });

        JevTransportException failure = assertThrows(JevTransportException.class,
                () -> new JevHttpTransport().send(request(), settings()));

        assertEquals(429, failure.statusCode());
        assertTrue(failure.retryable(), "429 still counts toward the circuit breaker");
        assertEquals(1, calls.get(), "no blocking backoff, so no immediate second attempt");
    }

    @Test
    void doesNotRetryAuthenticationFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 401, "{\"error\":\"bad key\"}");
        });

        JevTransportException failure = assertThrows(JevTransportException.class,
                () -> new JevHttpTransport().send(request(), settings()));

        assertEquals(401, failure.statusCode());
        assertFalse(failure.retryable());
        assertEquals(1, calls.get());
    }

    @Test
    void connectionFailureIsRetryableAndSurfacesAsTransportException() {
        TypeSafeSettings unreachable = new TypeSafeSettings("test-key", "http://127.0.0.1:9/v1/systemone", "jev-latest", 500);

        JevTransportException failure = assertThrows(JevTransportException.class,
                () -> new JevHttpTransport().send(request(), unreachable));

        assertTrue(failure.retryable());
        assertEquals(0, failure.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "{\"answers\":[]}",
            "{\"answers\":{\"intent\":null}}",
            "{\"answers\":{\"intent\":{\"type\":\"unknown\"}}}",
            "{\"answers\":{\"intent\":{\"type\":\"choice\",\"choice\":12}}}",
            "{\"answers\":{\"addressed\":{\"type\":\"noul\",\"noul\":\"yes\"}}}",
            "{\"answers\":{\"addressed\":{\"type\":\"noul\",\"noul\":1.2}}}",
            "{\"answers\":{\"fit\":{\"type\":\"score\"}}}",
            "{\"answers\":{\"intent\":{\"type\":\"choice\",\"choice\":\"grind\",\"confidence\":\"NaN\"}}}",
            "{\"answers\":{\"intent\":{\"type\":\"choice\",\"choice\":\"grind\",\"probabilities\":{\"grind\":-0.1}}}}"
    })
    void rejectsMalformedSuccessfulResponsesWithoutRetry(String body) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, body);
        });

        JevTransportException failure = assertThrows(JevTransportException.class,
                () -> new JevHttpTransport().send(request(), settings()));

        assertFalse(failure.retryable());
        assertEquals(1, calls.get());
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", handler);
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private TypeSafeSettings settings() {
        return new TypeSafeSettings("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone", "jev-latest", 5_000);
    }

    private static JevRequest request() {
        return JevRequest.builder(Map.of("message", "farm around here pls"))
                .choice("intent", "Which command?", Map.of("grind", "hunt anywhere", "patrol", "nearby only",
                        "chat_not_a_command", "not a command"))
                .noul("addressed", "Is it for the bot?")
                .score("fit", "How well?", List.of("poor fit", "ideal fit"))
                .build();
    }
}
