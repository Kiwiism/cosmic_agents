package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMapGraphWebServerTest {
    @Test
    void servesViewerAndHealthOnLoopback() throws Exception {
        AgentMapGraphWebServer server = new AgentMapGraphWebServer(0);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String root = "http://127.0.0.1:" + server.boundPort();

            HttpResponse<String> page = client.send(
                    HttpRequest.newBuilder(URI.create(root + "/mapgraph?id=103000000")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create(root + "/api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Agent Map Graph"));
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"UP\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsMutationMethods() throws Exception {
        AgentMapGraphWebServer server = new AgentMapGraphWebServer(0);
        server.start();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(
                                    URI.create("http://127.0.0.1:" + server.boundPort() + "/api/health"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(405, response.statusCode());
            assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        } finally {
            server.stop();
        }
    }
}
