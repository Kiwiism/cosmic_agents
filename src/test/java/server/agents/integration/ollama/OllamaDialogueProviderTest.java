package server.agents.integration.ollama;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.social.config.SocialDialogueSettings;
import server.agents.social.contracts.DialogueContextSnapshot;
import server.agents.social.contracts.DialogueMode;
import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;
import server.agents.social.contracts.DialogueStyleSnapshot;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaDialogueProviderTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsImmutableStructuredContextAndReturnsAttributedText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"response\":\"yo, what's up?\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        OllamaDialogueProvider provider = provider(30_000);

        DialogueResult result = provider.generate(request()).orElseThrow();

        assertEquals("yo, what's up?", result.displayText());
        assertEquals(DialogueResult.Source.MODEL, result.source());
        assertEquals("ollama:test-model", result.providerId());
        assertTrue(body.get().contains("friendly-casual-v1") || body.get().contains("friendly and casual"));
        assertTrue(body.get().contains("No prior relationship history"));
        assertTrue(!body.get().contains("client.Character"));
    }

    @Test
    void failedExternalServiceOpensCircuitAndReturnsEmpty() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        OllamaDialogueProvider provider = provider(30_000);

        assertTrue(provider.generate(request()).isEmpty());
        assertTrue(provider.generate(request()).isEmpty());
        assertEquals(1, calls.get());
    }

    private OllamaDialogueProvider provider(long circuitMs) {
        int port = server.getAddress().getPort();
        SocialDialogueSettings settings = new SocialDialogueSettings(
                DialogueMode.DIALOGUE_ONLY,
                "http://127.0.0.1:" + port,
                "test-model",
                2_000,
                180,
                80,
                4096,
                circuitMs);
        return new OllamaDialogueProvider(settings, HttpClient.newHttpClient());
    }

    private static DialogueRequest request() {
        return new DialogueRequest(
                "request-1",
                "casual.greeting",
                "Alice",
                "hello",
                new DialogueContextSnapshot(
                        100,
                        1,
                        "Mina",
                        "relaxed-v1",
                        new DialogueStyleSnapshot("friendly-casual-v1", 1, "friendly and casual",
                                25, 45, 40, 80, 15, List.of("yo")),
                        "between activities",
                        "No prior relationship history.",
                        100,
                        Map.of("world.mapId", "100000000")),
                List.of(),
                List.of("hey"),
                true,
                180,
                2_000);
    }
}
