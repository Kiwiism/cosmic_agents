package server.agents.progression;

import client.Character;
import client.Job;
import client.QuestStatus;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVictoriaQuestSchedulerRuntimeTest {
    @Test
    void explicitRetryClearsAQuestSuspensionWithoutLosingItsIdentity() {
        AgentVictoriaQuestSchedulerState state = new AgentVictoriaQuestSchedulerState();
        state.begin(1115, 103000000, 103000000, true);
        state.suspendAndDefer(20);

        assertTrue(state.suspended(1115));

        state.requestQuest(1115);

        assertEquals(1115, state.requestedQuestId());
        assertFalse(state.suspended(1115));
    }

    @Test
    void interactionOnlyBridgeAdvancesDirectlyToCompletion() {
        AgentVictoriaQuestRuntimeCatalog.Entry quest =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().find(2090).orElseThrow();
        Character agent = mock(Character.class);
        emptyUseInventory(agent);
        when(agent.getLevel()).thenReturn(14);
        when(agent.getMapId()).thenReturn(103000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).start(15, true, 0L);
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        state.begin(quest.questId(), 103000000, 103000000, true);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, quest.questId())).thenReturn(QuestStatus.Status.STARTED.getId());
        when(gateway.canCompleteQuest(agent, quest.questId(), quest.completeNpcId())).thenReturn(false);

        assertTrue(AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, 100L, gateway));

        assertEquals(AgentVictoriaQuestSchedulerState.Stage.TRAVEL_TO_COMPLETE, state.stage());
    }

    @Test
    void resumesAnIncompleteHuntingObjectiveWithoutReplayingQuestStart() {
        AgentVictoriaQuestRuntimeCatalog.Entry quest =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().find(1115).orElseThrow();
        AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective = quest.huntingObjectives().getFirst();
        AgentVictoriaQuestRuntimeCatalog.HuntMap huntMap = objective.huntMaps().getFirst();
        Character agent = mock(Character.class);
        emptyUseInventory(agent);
        when(agent.getId()).thenReturn(92);
        when(agent.getLevel()).thenReturn(20);
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getMapId()).thenReturn(huntMap.mapId());
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).start(30, true, 0L);
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        state.begin(quest.questId(), quest.startMapIds().getFirst(),
                quest.completeMapIds().getFirst(), true);
        state.huntMapId(huntMap.mapId());
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, quest.questId()))
                .thenReturn(QuestStatus.Status.STARTED.getId());
        when(gateway.canCompleteQuest(agent, quest.questId(), quest.completeNpcId())).thenReturn(false);
        when(gateway.itemCount(agent, objective.targetId())).thenReturn(0);

        assertTrue(AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).grind(entry, Set.copyOf(huntMap.targetMobIds()));
    }

    @Test
    void explicitQuestRequestBypassesMixedProgressionChoice() {
        AgentVictoriaQuestRuntimeCatalog.Entry quest =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository()
                        .find(1115).orElseThrow();
        Character agent = mock(Character.class);
        emptyUseInventory(agent);
        when(agent.getId()).thenReturn(93);
        when(agent.getLevel()).thenReturn(20);
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getMapId()).thenReturn(quest.completeMapIds().getFirst());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY)
                .start(21, true, quest.questId(), 0L);
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        state.requestQuest(quest.questId());
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, quest.questId()))
                .thenReturn(QuestStatus.Status.STARTED.getId());
        when(gateway.canCompleteQuest(agent, quest.questId(), quest.completeNpcId()))
                .thenReturn(false);

        assertTrue(AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, 100L, gateway));

        assertEquals(quest.questId(), state.questId());
    }

    private static void emptyUseInventory(Character agent) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.list()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.USE)).thenReturn(inventory);
    }
}
