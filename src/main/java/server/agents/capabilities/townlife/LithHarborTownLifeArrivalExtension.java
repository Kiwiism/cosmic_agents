package server.agents.capabilities.townlife;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.progression.AgentVictoriaRouteRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Lith Harbor's Shanks crossing and outstanding Biggs/Olaf handoff. */
final class LithHarborTownLifeArrivalExtension implements AgentTownLifeArrivalExtension {
    private static final int INTERACTION_DISTANCE_PX = 90;

    @Override
    public boolean tickTravel(AgentRuntimeEntry entry,
                              Character agent,
                              AgentTownLifeState state,
                              long nowMs,
                              PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() == state.townMapId()) {
            gateway.stop(entry);
            state.transition(AgentTownLifeState.Stage.COMPLETE_ARRIVAL,
                    nowMs + AgentTownLifeTimingPolicy.delay(agent, state, 1_500, 3_501));
            return true;
        }
        if (agent.getMapId() != MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID) {
            AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                    entry, agent, state.townMapId(), gateway, nowMs);
            return outcome.status() != AgentVictoriaRouteRuntime.Status.MOVING;
        }
        if (agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        Point shanks = gateway.npcPosition(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
        if (shanks == null) {
            state.transition(AgentTownLifeState.Stage.TRAVEL_TO_TOWN, nowMs + 2_000L);
            return true;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(shanks)
                > INTERACTION_DISTANCE_PX * INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, shanks, true);
            return false;
        }
        gateway.stop(entry);
        gateway.facePosition(agent, shanks);
        boolean entered = gateway.runNpcScript(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
        if (entered && agent.getMapId() == state.townMapId()) {
            state.transition(AgentTownLifeState.Stage.COMPLETE_ARRIVAL,
                    nowMs + AgentTownLifeTimingPolicy.delay(agent, state, 1_500, 3_501));
        } else {
            state.transition(AgentTownLifeState.Stage.TRAVEL_TO_TOWN, nowMs + 3_000L);
        }
        return true;
    }

    @Override
    public boolean tickArrival(AgentRuntimeEntry entry,
                               Character agent,
                               AgentTownLifeState state,
                               long nowMs,
                               PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() != state.townMapId()) {
            state.transition(AgentTownLifeState.Stage.RETURN_FROM_SHOP, nowMs);
            return true;
        }
        int questId = MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID;
        if (agent.getQuestStatus(questId) != QuestStatus.Status.STARTED.getId()) {
            settle(agent, state, nowMs);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        Point npc = gateway.npcPosition(agent, LithHarborTownLifeCatalog.BIGGS_NPC_ID);
        if (npc == null) {
            state.transition(AgentTownLifeState.Stage.COMPLETE_ARRIVAL, nowMs + 2_000L);
            return true;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc)
                > INTERACTION_DISTANCE_PX * INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            return false;
        }
        gateway.stop(entry);
        gateway.facePosition(agent, npc);
        boolean completed = gateway.canCompleteQuest(agent, questId, LithHarborTownLifeCatalog.BIGGS_NPC_ID)
                && gateway.completeQuest(agent, questId, LithHarborTownLifeCatalog.BIGGS_NPC_ID);
        if (completed || agent.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId()) {
            settle(agent, state, nowMs);
        } else {
            state.transition(AgentTownLifeState.Stage.COMPLETE_ARRIVAL, nowMs + 3_000L);
        }
        return true;
    }

    private static void settle(Character agent, AgentTownLifeState state, long nowMs) {
        state.transition(AgentTownLifeState.Stage.SETTLING,
                nowMs + AgentTownLifeTimingPolicy.delay(agent, state, 2_000, 5_001));
    }
}
