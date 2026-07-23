package server.agents.progression;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.io.IOException;
import java.util.Set;

/** Deterministic home-pack/rotation/grind bridge from instructor training to level 15. */
final class AgentLevel15CatchUpRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentLevel15CatchUpRuntime.class);
    private static final int NPC_DISTANCE_PX = config.AgentTuning.intValue("server.agents.progression.AgentLevel15CatchUpRuntime.NPC_DISTANCE_PX");

    private AgentLevel15CatchUpRuntime() {
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentCareerBuildBundle bundle = state.bundle();
        if (bundle == null) {
            return false;
        }
        AgentVictoriaLevel15CatalogRepository repository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();
        AgentVictoriaLevel15Catalog.Career career = repository.careerFor(bundle);
        AgentVictoriaLevel15Catalog.CatchUpPlan plan = career.catchUpPlan();
        return switch (state.stage()) {
            case HOME_QUEST_PACK -> runPack(entry, agent, state,
                    repository.questPack(plan.homePackId()),
                    AgentCareerProgressionState.Stage.POST_HOME_DECISION, nowMs, gateway);
            case POST_HOME_DECISION -> afterHome(state, plan, nowMs);
            case ROTATION_QUEST_PACK -> runPack(entry, agent, state,
                    repository.questPack(plan.rotationPackId()),
                    agent.getLevel() >= bundle.milestoneLevel()
                            ? AgentCareerProgressionState.Stage.FINAL_RETURN_TO_INSTRUCTOR
                            : AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE,
                    nowMs, gateway);
            case GRIND_TO_MILESTONE -> grind(entry, agent, state, bundle, plan.fallbackGrind(), nowMs, gateway);
            case FINAL_RETURN_TO_INSTRUCTOR -> finish(entry, agent, state, bundle, nowMs, gateway);
            default -> false;
        };
    }

    private static boolean runPack(AgentRuntimeEntry entry,
                                   Character agent,
                                   AgentCareerProgressionState state,
                                   AgentVictoriaLevel15Catalog.QuestPack pack,
                                   AgentCareerProgressionState.Stage completedStage,
                                   long nowMs,
                                   PrimitiveCapabilityGateway gateway) {
        AgentVictoriaQuestPackRuntime.Result result = AgentVictoriaQuestPackRuntime.tick(
                entry, agent, pack, nowMs, gateway);
        if (result == AgentVictoriaQuestPackRuntime.Result.COMPLETE) {
            state.questPackIndex(0);
            state.stage(completedStage, nowMs + AgentVictoriaProgressionPolicy.defaultPolicy()
                    .interactionDelayMs(agent.getId(), pack.packId().hashCode(), 3));
        }
        return result != AgentVictoriaQuestPackRuntime.Result.BLOCKED;
    }

    private static boolean afterHome(AgentCareerProgressionState state,
                                     AgentVictoriaLevel15Catalog.CatchUpPlan plan,
                                     long nowMs) {
        AgentCareerProgressionState.Stage next =
                plan.afterHomeStrategy() == AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK
                        ? AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK
                        : AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE;
        state.questPackIndex(0);
        state.stage(next, nowMs);
        return true;
    }

    private static boolean grind(AgentRuntimeEntry entry,
                                 Character agent,
                                 AgentCareerProgressionState state,
                                 AgentCareerBuildBundle bundle,
                                 AgentVictoriaLevel15Catalog.MilestoneGrind grind,
                                 long nowMs,
                                 PrimitiveCapabilityGateway gateway) {
        if (agent.getLevel() >= bundle.milestoneLevel()) {
            gateway.stop(entry);
            state.stage(AgentCareerProgressionState.Stage.FINAL_RETURN_TO_INSTRUCTOR, nowMs);
            return true;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, grind.huntingMapId(), gateway)) {
            return true;
        }
        gateway.grind(entry, Set.copyOf(grind.mobIds()));
        return true;
    }

    private static boolean finish(AgentRuntimeEntry entry,
                                  Character agent,
                                  AgentCareerProgressionState state,
                                  AgentCareerBuildBundle bundle,
                                  long nowMs,
                                  PrimitiveCapabilityGateway gateway) {
        if (AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway)) {
            return true;
        }
        Point npc = gateway.npcPosition(agent, bundle.instructorNpcId());
        if (npc == null) {
            String reason = "job instructor " + bundle.instructorNpcId()
                    + " is missing from checkpoint map " + bundle.instructorMapId();
            state.block(reason);
            AgentCareerObjectiveRuntime.block(entry, reason, nowMs);
            return false;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc) > NPC_DISTANCE_PX * NPC_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            return true;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        if (!AgentApBuildProfileService.autoAssign(entry, agent)) {
            AgentBuildService.autoAssignAp(entry, agent);
        }
        AgentBuildService.autoAssignSp(entry, agent);
        state.stage(AgentCareerProgressionState.Stage.COMPLETE, nowMs);
        AgentCareerObjectiveRuntime.succeed(entry, nowMs);
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        try {
            AgentVictoriaProgressionDiagnostics.captureMilestone(
                    entry, agent, "first-job-level15", nowMs);
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not persist level-15 milestone chr={}", agent.getId(), failure);
        }
        return false;
    }
}
