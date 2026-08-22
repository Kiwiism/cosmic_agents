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
import server.agents.runtime.activity.control.chat.AgentDirectorDomainContext;
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
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void ranksOnlySuppliedMapCandidatesWithExplicitMapleStoryDomain() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] response = ("{\"response\":\"{\\\"recommendations\\\":["
                    + "{\\\"actionId\\\":\\\"hunting-map:101010101\\\","
                    + "\\\"rationale\\\":\\\"high density with manageable danger\\\"},"
                    + "{\\\"actionId\\\":\\\"invented-map:1\\\","
                    + "\\\"rationale\\\":\\\"invalid\\\"},"
                    + "{\\\"actionId\\\":\\\"hunting-map:100020100\\\","
                    + "\\\"rationale\\\":\\\"compact terrain suits this Agent\\\"}]}\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/api/tags", exchange -> {
            byte[] response = "{\"models\":[{\"name\":\"test-model\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        DirectorLlmSettings settings = new DirectorLlmSettings(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-model",
                2_000, 2048, 256);
        OllamaDirectorProposalProvider provider = new OllamaDirectorProposalProvider(
                settings, HttpClient.newHttpClient());

        var advice = provider.recommendTrainingMaps(
                view(), "top 2 maps for lv16 grinding", domainContext()).orElseThrow();

        assertEquals(List.of("hunting-map:101010101", "hunting-map:100020100"),
                advice.selections().stream().map(selection -> selection.actionId()).toList());
        assertTrue(requestBody.get().contains("Cosmic MapleStory v83"));
        assertTrue(provider.health().ready());
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

    private static AgentDirectorDomainContext domainContext() {
        return new AgentDirectorDomainContext(
                "Cosmic MapleStory v83", "cosmic-v83-test", 16, 16, 2,
                List.of(candidate(100020100, "Henesys Pig Farm", 1),
                        candidate(101010101, "The Tree That Grew II", 2)));
    }

    private static AgentDirectorDomainContext.TrainingMapCandidate candidate(
            int mapId, String name, int rank) {
        return new AgentDirectorDomainContext.TrainingMapCandidate(
                "hunting-map:" + mapId, "Hunt — " + name, mapId, name,
                rank, 100 - rank, 15, 18, 1, 3, "compact-platforms",
                "catalog rationale", List.of(), List.of("dense"), List.of("rope traversal"),
                List.of(new AgentDirectorDomainContext.SpawnFact(
                        100100, "Test Mob", 10, 12, "primary")), true);
    }
}
