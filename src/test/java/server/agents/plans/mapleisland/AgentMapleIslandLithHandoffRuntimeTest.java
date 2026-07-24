package server.agents.plans.mapleisland;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentPlanRepository;
import server.agents.plans.AgentPlanSessionState;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapleIslandLithHandoffRuntimeTest {
    @Test
    void successfulShipTransferHandsTheAgentToTownLifeArrival() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        when(agent.getMapId()).thenReturn(104_000_000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentPlanSessionState plan =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        plan.start(
                AgentPlanRepository.defaultRepository().require(
                        AgentMapleIslandLithHandoffRuntime.TRANSFER_PLAN_ID),
                "successful-transfer", AgentPlanStartRequest.EMPTY, 1_000L);
        plan.terminal(AgentPlanExecutionStatus.SUCCEEDED, "arrived on the ship");
        AgentMapleIslandLithHandoffState handoff = entry.capabilityStates()
                .require(AgentMapleIslandLithHandoffState.STATE_KEY);
        handoff.request(1_500L);

        assertTrue(AgentMapleIslandLithHandoffRuntime.tick(entry, agent, 2_000L));

        assertTrue(AgentTownLifeRuntime.active(entry));
        assertEquals(AgentTownLifeState.Stage.TRAVEL_TO_TOWN,
                entry.capabilityStates().require(AgentTownLifeState.STATE_KEY).stage());
        assertEquals(AgentMapleIslandLithHandoffState.Stage.COMPLETE, handoff.stage());
    }

    @Test
    void explicitRequestRestartsAFailedTransferCheckpointAtSouthperry() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID);
        when(agent.getQuestStatus(
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID))
                .thenReturn((byte) QuestStatus.Status.STARTED.getId());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentPlanSessionState plan =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        plan.start(
                AgentPlanRepository.defaultRepository().require(
                        AgentMapleIslandLithHandoffRuntime.TRANSFER_PLAN_ID),
                "failed-transfer", AgentPlanStartRequest.EMPTY, 1_000L);
        plan.terminal(AgentPlanExecutionStatus.FAILED, "step timed out");
        AgentMapleIslandLithHandoffState handoff = entry.capabilityStates()
                .require(AgentMapleIslandLithHandoffState.STATE_KEY);
        handoff.request(2_000L);

        assertTrue(AgentMapleIslandLithHandoffRuntime.tick(entry, agent, 2_000L));
        assertEquals(AgentPlanExecutionStatus.ACTIVE,
                AgentUniversalPlanRuntime.status(entry));
        assertEquals(AgentMapleIslandLithHandoffState.Stage.TRANSFERRING,
                handoff.stage());
    }
}
