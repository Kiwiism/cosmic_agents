package server.agents.economy.integration.cosmic;

import client.Character;
import client.Client;
import client.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import server.agents.economy.persistence.EconomyBootstrapStore;
import server.agents.economy.persistence.EconomyParticipantBindingStore;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.scenario.EconomyWorldPort;
import server.economy.EconomyTaxOverride;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CosmicEconomyWorldAdapterRestoreTest {
    private final List<UUID> runIds = new java.util.ArrayList<>();

    @AfterEach
    void releaseCommerceControl() {
        runIds.forEach(runId -> server.agents.runtime.AgentCommerceControlRuntime.release(
                "economy:" + runId));
    }

    @Test
    void typedSessionDrainsAfterMeaningfulIdleTimeout() {
        Character warrior = character(101, 100);
        when(warrior.getMapId()).thenReturn(910000000);
        CosmicEconomyWorldAdapter.MarketBehavior market = mock(CosmicEconomyWorldAdapter.MarketBehavior.class);
        when(market.perform(any(), any(), any())).thenReturn(new EconomyWorldPort.MarketDirective(
                java.util.Optional.empty(), java.util.Optional.of(Instant.EPOCH.plusSeconds(1))));
        when(market.drainForRelease(any(), any(), any())).thenAnswer(invocation ->
                new EconomyWorldPort.MarketDirective(java.util.Optional.of(
                        invocation.getArgument(2, Instant.class)), java.util.Optional.empty()));
        CosmicEconomyWorldAdapter world = new CosmicEconomyWorldAdapter(runId(), 1,
                "config", "catalog", ignored -> warrior, market, ignored -> new EconomyTaxOverride(0, 0),
                EconomyParticipantBindingStore.NO_OP, EconomyBootstrapStore.NO_OP,
                (profile, character) -> { }, (profile, character) -> { });
        CommerceParticipant profile = profile("agent-1", "warrior");
        UUID sessionId = UUID.randomUUID();
        world.restoreState(Map.of("schemaVersion", 1, "boundAgentIds", List.of("agent-1"),
                "offscreenAgentIds", List.of(), "activeSessions", Map.of("agent-1", Map.of(
                        "sessionId", sessionId.toString(), "requestId", UUID.randomUUID().toString(),
                        "enteredAt", Instant.EPOCH.toString(),
                        "expiresAt", Instant.EPOCH.plusSeconds(1800).toString(),
                        "maximumIdleMillis", 300_000, "lastProgressRevision", 0,
                        "lastProgressAt", Instant.EPOCH.toString())), "market", Map.of()),
                Map.of("agent-1", profile));

        world.performMarketCycle(sessionId, profile, Instant.EPOCH);
        var timedOut = world.performMarketCycle(sessionId, profile, Instant.EPOCH.plusSeconds(300));

        assertEquals(true, timedOut.releaseRequested());
        assertEquals("SESSION_IDLE_TIMEOUT", timedOut.reason());
        verify(market).drainForRelease(warrior, profile, Instant.EPOCH.plusSeconds(300));
    }

    @Test
    void admittedAgentCanBeginPhysicalMarketCycleFromEntrance() {
        Character warrior = character(101, 100);
        when(warrior.getMapId()).thenReturn(910000000);
        CosmicEconomyWorldAdapter.MarketBehavior market = mock(CosmicEconomyWorldAdapter.MarketBehavior.class);
        when(market.perform(any(), any(), any())).thenReturn(EconomyWorldPort.MarketDirective.idle());
        CosmicEconomyWorldAdapter world = new CosmicEconomyWorldAdapter(runId(), 1,
                "config", "catalog", ignored -> warrior, market,
                mock(CosmicEconomyWorldAdapter.ActivityPlanner.class),
                mock(CosmicEconomyWorldAdapter.OffscreenPresence.class),
                mock(CosmicFarmSettlementService.class), ignored -> new EconomyTaxOverride(0, 0),
                EconomyParticipantBindingStore.NO_OP, EconomyBootstrapStore.NO_OP);
        CommerceParticipant profile = profile("agent-1", "warrior");

        world.restoreState(Map.of("schemaVersion", 1,
                        "boundAgentIds", List.of("agent-1"),
                        "offscreenAgentIds", List.of(), "market", Map.of()),
                Map.of("agent-1", profile));
        assertEquals(EconomyWorldPort.MarketDirective.idle(),
                world.performMarketCycle(profile, java.time.Instant.EPOCH));
        verify(market).perform(warrior, profile, java.time.Instant.EPOCH);
    }

    @Test
    void restoresBindingsParticipantsMarketAndDetachedPresence() {
        Character warrior = character(101, 100);
        Character magician = character(102, 200);
        Map<String, Character> directory = Map.of("agent-1", warrior, "agent-2", magician);
        CosmicEconomyWorldAdapter.MarketBehavior market = mock(CosmicEconomyWorldAdapter.MarketBehavior.class);
        CosmicEconomyWorldAdapter.OffscreenPresence presence = mock(CosmicEconomyWorldAdapter.OffscreenPresence.class);
        AtomicReference<List<String>> admitted = new AtomicReference<>(new java.util.ArrayList<>());
        CosmicEconomyWorldAdapter world = new CosmicEconomyWorldAdapter(runId(), 1,
                "config", "catalog", directory::get, market,
                mock(CosmicEconomyWorldAdapter.ActivityPlanner.class), presence,
                mock(CosmicFarmSettlementService.class), ignored -> new EconomyTaxOverride(0, 0),
                EconomyParticipantBindingStore.NO_OP, EconomyBootstrapStore.NO_OP,
                (profile, character) -> admitted.get().add(profile.agentId() + ':' + character.getId()));
        CommerceParticipant first = profile("agent-1", "warrior");
        CommerceParticipant second = profile("agent-2", "magician");
        Map<String, Object> marketState = Map.of("phase", "BROWSING");
        Map<String, Object> state = Map.of("schemaVersion", 1,
                "boundAgentIds", List.of("agent-1", "agent-2"),
                "offscreenAgentIds", List.of("agent-2"), "market", marketState);

        world.restoreState(state, Map.of("agent-1", first, "agent-2", second));

        assertEquals(List.of("agent-1:101", "agent-2:102"), admitted.get());
        verify(presence).restoreDetached(magician);
        verify(market).restoreState(marketState);
        assertEquals(2, world.snapshotState().get("boundAgentIds") instanceof List<?> values ? values.size() : 0);
    }

    private static Character character(int id, int jobId) {
        Character character = mock(Character.class);
        Client gameClient = mock(Client.class);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(gameClient);
        when(gameClient.getChannel()).thenReturn(1);
        when(character.getJob()).thenReturn(Job.getById(jobId));
        return character;
    }

    private UUID runId() {
        UUID runId = UUID.randomUUID();
        runIds.add(runId);
        return runId;
    }

    private static CommerceParticipant profile(String id, String family) {
        return new CommerceParticipant(id, family, .5, .5, .5, .5, .5, .5, 24, .5, .5);
    }
}
