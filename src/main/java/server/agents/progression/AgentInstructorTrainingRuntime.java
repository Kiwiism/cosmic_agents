package server.agents.progression;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;

/** Runs the four normal instructor quests before handing off to the level-15 catch-up plan. */
public final class AgentInstructorTrainingRuntime {
    private static final int NPC_DISTANCE_PX = config.AgentTuning.intValue("server.agents.progression.AgentInstructorTrainingRuntime.NPC_DISTANCE_PX");
    private static final long NPC_DELAY_MS = config.AgentTuning.longValue("server.agents.progression.AgentInstructorTrainingRuntime.NPC_DELAY_MS");

    private AgentInstructorTrainingRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentCareerBuildBundle bundle = state.bundle();
        if (bundle == null || agent.getJob().getId() != bundle.firstJobId()) {
            return false;
        }
        List<AgentInstructorTrainingStep> steps = AgentInstructorTrainingCatalog.steps(bundle);
        int index = reconcileCompleted(state, gateway, agent, steps);
        if (index >= steps.size()) {
            state.questPackIndex(0);
            state.stage(AgentCareerProgressionState.Stage.HOME_QUEST_PACK, nowMs);
            return true;
        }

        AgentInstructorTrainingStep step = steps.get(index);
        if (state.stage() != AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING) {
            state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, nowMs);
        }
        int status = gateway.questStatus(agent, step.questId());
        if (status == 0) {
            if (AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway)) {
                return true;
            }
            if (!state.ready(nowMs) || !approachNpc(entry, agent, bundle.instructorNpcId(), gateway)) {
                return true;
            }
            if (gateway.canStartQuest(agent, step.questId(), bundle.instructorNpcId())
                    && gateway.startQuest(agent, step.questId(), bundle.instructorNpcId())) {
                state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, nowMs + NPC_DELAY_MS);
            }
            return true;
        }
        if (gateway.canCompleteQuest(agent, step.questId(), bundle.instructorNpcId())) {
            if (AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway)) {
                return true;
            }
            if (!state.ready(nowMs) || !approachNpc(entry, agent, bundle.instructorNpcId(), gateway)) {
                return true;
            }
            if (gateway.completeQuest(agent, step.questId(), bundle.instructorNpcId())) {
                state.trainingQuestIndex(index + 1);
                state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, nowMs + NPC_DELAY_MS);
            }
            return true;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, step.huntingMapId(), gateway)) {
            return true;
        }
        gateway.grind(entry, step.mobIds());
        return true;
    }

    private static int reconcileCompleted(AgentCareerProgressionState state,
                                          PrimitiveCapabilityGateway gateway,
                                          Character agent,
                                          List<AgentInstructorTrainingStep> steps) {
        int index = state.trainingQuestIndex();
        while (index < steps.size() && gateway.questStatus(agent, steps.get(index).questId()) == 2) {
            index++;
        }
        state.trainingQuestIndex(index);
        return index;
    }

    private static boolean approachNpc(AgentRuntimeEntry entry,
                                       Character agent,
                                       int npcId,
                                       PrimitiveCapabilityGateway gateway) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return false;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc) > NPC_DISTANCE_PX * NPC_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            return false;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        return true;
    }
}
