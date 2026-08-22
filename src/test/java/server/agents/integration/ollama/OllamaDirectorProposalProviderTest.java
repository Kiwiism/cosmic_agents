package server.agents.integration.ollama;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorActionAvailability;
import server.agents.runtime.activity.control.AgentDirectorActivityProjection;
import server.agents.runtime.activity.control.AgentDirectorEnergySnapshot;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.AgentDirectorProfileSnapshot;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaDirectorProposalProviderTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void acceptsOnlyAnExecutableActionFromCurrentSnapshot() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = ("{\"response\":\"{\\\"actionId\\\":\\\"town-life:101000000\\\","
                    + "\\\"rationale\\\":\\\"recover before questing\\\","
                    + "\\\"expectedEnergyDelta\\\":12}\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        DirectorLlmSettings settings = new DirectorLlmSettings(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-model",
                2_000, 1024, 80);
        OllamaDirectorProposalProvider provider = new OllamaDirectorProposalProvider(
                settings, HttpClient.newHttpClient());

        var selected = provider.select(view(), "let Mira recover").orElseThrow();

        assertEquals("town-life:101000000", selected.actionId());
        assertEquals(12, selected.expectedEnergyDelta());
        assertTrue(selected.provider().contains("test-model"));
    }

    private static AgentDirectorExecutiveView view() {
        AgentDirectorExecutiveView view = mock(AgentDirectorExecutiveView.class);
        when(view.context()).thenReturn(new AgentWorldContext(1, 1_000L, 27, "Mira",
                20, 200, 101000000, 500, 500, 800, 800, 10_000, true, false,
                Set.of(), Set.of(), null, "", "", "", "VICTORIA", Map.of()));
        when(view.energy()).thenReturn(new AgentDirectorEnergySnapshot(
                30, 70, 50, 5, "LOW", 1_000L));
        when(view.profile()).thenReturn(new AgentDirectorProfileSnapshot(
                "explorer-v1", 1, Map.of("curiosity", 90)));
        when(view.activity()).thenReturn(new AgentDirectorActivityProjection(
                "questing", "rest", "", "", "", ""));
        when(view.actions()).thenReturn(List.of(new AgentDirectorAction(
                "town-life:101000000", "Visit Ellinia",
                AgentDirectorActionAvailability.RECOMMENDED, "recover energy",
                AgentWorldDirectiveType.START_ACTIVITY,
                AgentActivityKind.TOWN_LIFE,
                AgentWorldActivityRequestType.TOWN_LIFE_VISIT,
                "town-life:101000000", Map.of("mapId", "101000000"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 200, false)));
        return view;
    }
}
