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
        assertEquals("", state.terminalReason());
    }

    @Test
    void suspensionRetainsTheTypedAdvisorReasonForActivityReporting() {
        AgentVictoriaQuestSchedulerState state = new AgentVictoriaQuestSchedulerState();
        state.begin(2010, 104000000, 102000000, true);

        state.suspendAndDefer(25,
                "SAFE_BOUNDARY_REQUESTED: no meaningful quest progress remains");

        assertTrue(state.suspended(2010));
        assertEquals("SAFE_BOUNDARY_REQUESTED: no meaningful quest progress remains",
                state.terminalReason());
    }

    @Test
    void explicitDifferentQuestRequestPreemptsOnlyTheSchedulerCursor() {
        AgentVictoriaQuestSchedulerState state = new AgentVictoriaQuestSchedulerState();
        state.begin(28267, 100000000, 100000000, true);
        state.objectiveIndex(1);
        state.huntMapId(100000003);

        state.requestQuest(28274);

        assertEquals(28274, state.requestedQuestId());
        assertFalse(state.active());
        assertEquals(AgentVictoriaQuestSchedulerState.Stage.IDLE, state.stage());
        assertEquals(0, state.objectiveIndex());
        assertEquals(0, state.huntMapId());
        assertFalse(state.failed(28267));
        assertFalse(state.suspended(28267));
    }

    @Test
    void shopAttemptMarkerIsScopedToOneObjective() {
        AgentVictoriaQuestSchedulerState state = new AgentVictoriaQuestSchedulerState();
        state.begin(2165, 120000300, 120000300, true);

        state.markShopAttemptedForCurrentObjective();
        assertTrue(state.shopAttemptedForCurrentObjective());

        state.objectiveIndex(1);
        assertFalse(state.shopAttemptedForCurrentObjective());
    }

    @Test
    void explicitSameQuestResumeStartsWithFreshStruggleEvidence() {
        AgentVictoriaQuestSchedulerState state = new AgentVictoriaQuestSchedulerState();
        state.begin(2010, 102000000, 102000000, true);
        state.beginAttempt(100L, 104000000, 3, new Point(0, 0), 0, 40, 10);
        state.recordNavigationFailure();
        state.recordRetry();
        state.assessedAt(500L);

        state.requestQuest(2010);

        assertTrue(state.active());
        assertEquals(2010, state.questId());
        assertEquals(0L, state.attemptStartedAtMs());
        assertEquals(-1L, state.lastObjectiveProgressAtMs());
        assertEquals(-1L, state.lastNavigationProgressAtMs());
        assertEquals(0, state.navigationFailureCount());
        assertEquals(0, state.retryCount());
        assertEquals(0L, state.nextAssessmentAtMs());
    }

    @Test
    void sameMapRegionAndPositionMovementRefreshNavigationProgress() {
        AgentVictoriaQuestSchedulerState state = new AgentVictoriaQuestSchedulerState();
        state.beginAttempt(100L, 104000000, 3, new Point(0, 0), 0, 40, 10);

        state.observeAttempt(104000000, 4, new Point(30, 0), 0, 500L);
        assertEquals(500L, state.lastNavigationProgressAtMs());

        state.observeAttempt(104000000, 4, new Point(180, 0), 0, 900L);
        assertEquals(900L, state.lastNavigationProgressAtMs());
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

    @Test
    void explicitSealRequestReportsTheMissingProducerChainItem() {
        AgentVictoriaQuestRuntimeCatalog.Entry quest =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository()
                        .find(28257).orElseThrow();
        Character agent = mock(Character.class);
        emptyUseInventory(agent);
        when(agent.getId()).thenReturn(94);
        when(agent.getLevel()).thenReturn(20);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMapId()).thenReturn(quest.startMapIds().getFirst());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY)
                .start(21, true, quest.questId(), 0L);
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        state.requestQuest(quest.questId());
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, quest.questId()))
                .thenReturn(QuestStatus.Status.NOT_STARTED.getId());
        when(gateway.canStartQuest(agent, quest.questId(), quest.startNpcId()))
                .thenReturn(false);
        when(gateway.itemCount(agent, 4032496)).thenReturn(0);

        assertFalse(AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, 100L, gateway));

        assertTrue(state.terminalReason().contains("Devil Hunter's Necklace"));
        assertTrue(state.terminalReason().contains("28179"));
        assertTrue(state.terminalReason().contains("owned 0"));
    }

    @Test
    void explicitFinalSealRequestReportsTheIncompleteSealQuest() {
        AgentVictoriaQuestRuntimeCatalog.Entry quest =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository()
                        .find(28262).orElseThrow();
        Character agent = mock(Character.class);
        emptyUseInventory(agent);
        when(agent.getId()).thenReturn(95);
        when(agent.getLevel()).thenReturn(20);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMapId()).thenReturn(quest.startMapIds().getFirst());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY)
                .start(21, true, quest.questId(), 0L);
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        state.requestQuest(quest.questId());
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, quest.questId()))
                .thenReturn(QuestStatus.Status.NOT_STARTED.getId());
        for (int sealQuestId : List.of(28257, 28258, 28260, 28261)) {
            when(gateway.questStatus(agent, sealQuestId))
                    .thenReturn(QuestStatus.Status.COMPLETED.getId());
        }
        when(gateway.questStatus(agent, 28259))
                .thenReturn(QuestStatus.Status.NOT_STARTED.getId());
        when(gateway.canStartQuest(agent, quest.questId(), quest.startNpcId()))
                .thenReturn(false);

        assertFalse(AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, 100L, gateway));

        assertTrue(state.terminalReason().contains("quest 28259"));
        assertTrue(state.terminalReason().contains("current state 0"));
    }

    private static void emptyUseInventory(Character agent) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.list()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.USE)).thenReturn(inventory);
    }
}
