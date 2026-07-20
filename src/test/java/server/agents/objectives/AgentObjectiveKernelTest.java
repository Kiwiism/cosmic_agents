package server.agents.objectives;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentObjectiveKernelTest {
    @Test
    void recordsAndPublishesTheObjectiveLifecycle() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition objective = new AgentObjectiveDefinition(
                "maple-41-1", "quest", 10, 10_000L, 2,
                AgentObjectiveSource.QUEST_PLAN, "maple-v1", "run-1");

        AgentObjectiveKernel.start(entry, objective, 100L);
        assertEquals(objective, AgentObjectiveKernel.active(entry));
        assertTrue(AgentObjectiveKernel.transition(entry, objective.objectiveId(),
                AgentObjectiveStatus.SUCCEEDED, "quest complete", 200L));

        assertNull(AgentObjectiveKernel.active(entry));
        AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        assertEquals(2, state.journalSnapshot().size());
        assertEquals(AgentObjectiveStatus.ACTIVE, state.journalSnapshot().get(0).status());
        assertEquals(AgentObjectiveStatus.SUCCEEDED, state.journalSnapshot().get(1).status());
        assertEquals(2, AgentSessionEventRuntime.bus(entry).snapshot().queued());
        assertFalse(AgentObjectiveKernel.transition(entry, objective.objectiveId(),
                AgentObjectiveStatus.FAILED, "late failure", 300L));
    }

    @Test
    void suspendsForegroundForMaintenanceThenResumesIt() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition quest = new AgentObjectiveDefinition(
                "quest-42", "quest", 10, 10_000L, 2,
                AgentObjectiveSource.QUEST_PLAN, "quest-v1", "run-42");
        AgentObjectiveDefinition resupply = new AgentObjectiveDefinition(
                "resupply-42", "maintenance.resupply", 100, 20_000L, 1,
                AgentObjectiveSource.RECOVERY_POLICY, "resupply-v1", "run-42");

        AgentObjectiveKernel.start(entry, quest, 100L);
        assertTrue(AgentObjectiveKernel.suspendFor(entry, resupply, "HP potions critical", 200L));
        assertEquals(resupply, AgentObjectiveKernel.active(entry));
        assertEquals(quest, entry.capabilityStates().require(AgentObjectiveState.STATE_KEY)
                .suspendedSnapshot().get(0).objective());

        assertTrue(AgentObjectiveKernel.completeAndResume(entry, resupply.objectiveId(),
                "supplies restored", 300L));
        assertEquals(quest, AgentObjectiveKernel.active(entry));
        assertTrue(entry.capabilityStates().require(AgentObjectiveState.STATE_KEY)
                .suspendedSnapshot().isEmpty());
        assertEquals(AgentObjectiveStatus.RESUMED,
                entry.capabilityStates().require(AgentObjectiveState.STATE_KEY)
                        .journalSnapshot().get(4).status());
    }

    @Test
    void failedMaintenanceStillRestoresForegroundIntent() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(43);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition training = new AgentObjectiveDefinition(
                "training-43", "progression.victoria-training", 10, Long.MAX_VALUE, 2,
                AgentObjectiveSource.PROGRESSION_POLICY, "training-v1", "run-43");
        AgentObjectiveDefinition resupply = new AgentObjectiveDefinition(
                "resupply-43", "maintenance.resupply", 100, 20_000L, 1,
                AgentObjectiveSource.RECOVERY_POLICY, "resupply-v1", "run-43");

        AgentObjectiveKernel.start(entry, training, 100L);
        AgentObjectiveKernel.suspendFor(entry, resupply, "MP potions empty", 200L);

        assertTrue(AgentObjectiveKernel.finishAndResume(entry, resupply.objectiveId(),
                AgentObjectiveStatus.FAILED, "no affordable supplier", 300L));
        assertEquals(training, AgentObjectiveKernel.active(entry));
        assertEquals(AgentObjectiveStatus.FAILED,
                entry.capabilityStates().require(AgentObjectiveState.STATE_KEY)
                        .journalSnapshot().get(3).status());
    }
}
