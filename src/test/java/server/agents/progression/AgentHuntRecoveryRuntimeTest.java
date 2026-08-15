package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentHuntRecoveryRuntimeTest {
    @Test
    void instanceExhaustionReentersOnceThenActivatesExternalFallback() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "instructor:2195";

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030000, 35, 0, true, 1_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.REENTER_INSTANCE,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030000, 35, 0, true, 17_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030001, 35, 0, true, 18_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.RESELECT,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030001, 35, 0, true, 34_000L));

        AgentHuntRecoveryRuntime.failMaps(entry, objective, 35,
                Set.of(120010000, 912030000, 912030001), 40_000L);
        assertTrue(AgentHuntRecoveryRuntime.fallbackActive(entry, objective, 35, 40_001L));
        assertTrue(AgentHuntRecoveryRuntime.failedMaps(entry, objective, 35, 40_001L)
                .containsAll(Set.of(120010000, 912030000, 912030001)));
    }

    @Test
    void relevantDamageHeartbeatDefersReselectionUntilHardKillDeadline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "instructor:2196";
        int mapId = 103030000;

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 8, 1, false, 1_000L));

        AgentHuntRecoveryRuntime.recordRelevantDamage(entry, mapId, 45_000L);
        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 8, 1, false, 47_000L));

        AgentHuntRecoveryRuntime.recordRelevantDamage(entry, mapId, 134_999L);
        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 8, 1, false, 134_999L));

        AgentHuntRecoveryRuntime.recordRelevantDamage(entry, mapId, 135_000L);
        assertEquals(AgentHuntRecoveryRuntime.Observation.RESELECT,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 8, 1, false, 135_000L));
    }

    @Test
    void relevantKillsRenewCollectionWorkWithoutRequiringAQuestDrop() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "shared:ribbon-pig-drop";
        int mapId = 100030000;

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, 1_000L));
        AgentHuntRecoveryRuntime.recordRelevantDamage(entry, mapId, 45_000L);
        AgentHuntRecoveryRuntime.recordRelevantKill(entry, mapId, 60_000L);
        AgentHuntRecoveryRuntime.recordRelevantDamage(entry, mapId, 134_999L);

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, 134_999L));
    }

    @Test
    void navigationWarmupDoesNotConsumeTheHuntProgressGrace() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "shared:ribbon-pig-drop";
        int mapId = 100030000;

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, false, 1_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, true, 100_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, false, 100_001L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.RESELECT,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, false, 145_000L));
    }

    @Test
    void navigationWarmupPauseRemainsBounded() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "shared:ribbon-pig-drop";
        int mapId = 100030000;

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, true, 1_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.RESELECT,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, mapId, 19, 1, false, true, 181_000L));
    }
}
