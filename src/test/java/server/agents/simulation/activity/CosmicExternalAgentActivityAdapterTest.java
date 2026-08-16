package server.agents.simulation.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.integration.cosmic.EconomyParticipantRegistry;
import server.agents.economy.session.CommerceParticipant;
import server.economy.EconomyTaxOverride;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CosmicExternalAgentActivityAdapterTest {
    @Test
    void requiresEconomyReleaseBeforeTakingExternalOwnership() {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(101);
        when(character.getMapId()).thenReturn(910000001, 910000001, 910000001, 910000000);
        CommerceParticipant profile = profile();
        EconomyParticipantRegistry participants = new EconomyParticipantRegistry(id -> character);
        participants.admitted(profile, character);
        FarmSessionPlan plan = new FarmSessionPlan("farm-1", "calibration-1", "agent-1", 100000000,
                Instant.EPOCH, Duration.ofHours(1), 1, List.of(), Set.of(), List.of());
        CosmicExternalAgentActivityAdapter.Presence presence =
                mock(CosmicExternalAgentActivityAdapter.Presence.class);
        var adapter = new CosmicExternalAgentActivityAdapter(UUID.randomUUID(), "config", "catalog",
                participants, (agent, value, at) -> plan, mock(RuleExactFarmResolver.class), presence,
                (agent, outcome, random) -> outcome, at -> new EconomyTaxOverride(0, 0));

        assertThrows(IllegalStateException.class, () -> adapter.plan(profile, Instant.EPOCH));
        participants.released(profile, character);
        assertEquals(plan, adapter.plan(profile, Instant.EPOCH));
        adapter.begin(profile, plan, Instant.EPOCH);
        verify(presence).leave(character, Instant.EPOCH);
        adapter.returnToEconomyEntrance(profile, Instant.EPOCH.plusSeconds(3600));
        verify(presence).enterEconomyEntrance(character, Instant.EPOCH.plusSeconds(3600));
    }

    private static CommerceParticipant profile() {
        return new CommerceParticipant("agent-1", "BEGINNER", .5, .5, .5, .5, .5, .5,
                24, .5, .5);
    }
}
