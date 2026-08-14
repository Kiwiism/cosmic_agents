package server.agents.economy.integration.cosmic;

import client.Character;
import client.Client;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.economy.persistence.EconomyBootstrapStore;
import server.agents.economy.persistence.EconomyParticipantBindingStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.economy.EconomyTaxOverride;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CosmicEconomyWorldAdapterRestoreTest {
    @Test
    void restoresBindingsParticipantsMarketAndDetachedPresence() {
        Character warrior = character(101, 100);
        Character magician = character(102, 200);
        Map<String, Character> directory = Map.of("agent-1", warrior, "agent-2", magician);
        CosmicEconomyWorldAdapter.MarketBehavior market = mock(CosmicEconomyWorldAdapter.MarketBehavior.class);
        CosmicEconomyWorldAdapter.OffscreenPresence presence = mock(CosmicEconomyWorldAdapter.OffscreenPresence.class);
        AtomicReference<List<String>> admitted = new AtomicReference<>(new java.util.ArrayList<>());
        CosmicEconomyWorldAdapter world = new CosmicEconomyWorldAdapter(UUID.randomUUID(), 1,
                "config", "catalog", directory::get, market,
                mock(CosmicEconomyWorldAdapter.ActivityPlanner.class), presence,
                mock(CosmicFarmSettlementService.class), ignored -> new EconomyTaxOverride(0, 0),
                EconomyParticipantBindingStore.NO_OP, EconomyBootstrapStore.NO_OP,
                (profile, character) -> admitted.get().add(profile.agentId() + ':' + character.getId()));
        EconomyAgentProfile first = profile("agent-1", "warrior");
        EconomyAgentProfile second = profile("agent-2", "magician");
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

    private static EconomyAgentProfile profile(String id, String family) {
        return new EconomyAgentProfile(id, family, .5, .5, .5, .5, .5, .5, 24, .5, .5);
    }
}
