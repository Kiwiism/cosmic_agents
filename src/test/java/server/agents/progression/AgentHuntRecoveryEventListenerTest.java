package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentHuntRecoveryEventListenerTest {
    @Test
    void onlyPreferredObjectiveMobsRenewHuntWork() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "shared:ribbon-pig-drop";
        int mapId = 100030000;
        AgentCombatObjectiveTargetStateRuntime.setTargetPreferences(
                entry, Set.of(1210101), Set.of(1210100));
        AgentHuntRecoveryRuntime.observe(entry, objective, mapId, 19, 1, false, 1_000L);
        AgentHuntRecoveryEventListener listener = new AgentHuntRecoveryEventListener(entry);

        listener.onAgentEvent(new AgentMobDamagedEvent(
                1, 45_000L, mapId, 1210100, 10, 5, objective));
        assertEquals(AgentHuntRecoveryRuntime.Observation.RESELECT,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, 46_001L));

        AgentHuntRecoveryRuntime.clear(entry, objective);
        AgentHuntRecoveryRuntime.observe(entry, objective, mapId, 19, 1, false, 1_000L);
        listener.onAgentEvent(new AgentMobDamagedEvent(
                1, 45_000L, mapId, 1210101, 11, 5, objective));
        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 60_000L, mapId, 1210101, 11, 10, objective));
        listener.onAgentEvent(new AgentMobDamagedEvent(
                1, 134_999L, mapId, 1210101, 12, 5, objective));

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, 134_999L));
    }
}
