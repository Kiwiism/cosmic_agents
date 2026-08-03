package server.agents.progression;

import client.Character;
import constants.game.ExpTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.io.IOException;
import java.util.Set;

/** Deterministic home-pack/rotation/grind bridge from instructor training to level 15. */
final class AgentLevel15CatchUpRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentLevel15CatchUpRuntime.class);
    private static final int HOME_GRIND_MAX_REMAINING_PERCENT = Math.clamp(config.AgentTuning.intValue(
            "server.agents.progression.AgentLevel15CatchUpRuntime.HOME_GRIND_MAX_REMAINING_PERCENT"), 0, 100);

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
                    plan.homePackId(),
                    AgentCareerProgressionState.Stage.POST_HOME_DECISION, nowMs, gateway);
            case POST_HOME_DECISION -> afterHome(agent, state, bundle, plan, nowMs);
            case HOME_GRIND_TO_MILESTONE ->
                    grind(entry, agent, state, bundle, career.milestoneGrind(), nowMs, gateway);
            case ROTATION_QUEST_PACK -> runRotationPack(
                    entry, agent, state, plan.rotationPackId(), bundle.milestoneLevel(), nowMs, gateway);
            case GRIND_TO_MILESTONE -> grind(entry, agent, state, bundle, plan.fallbackGrind(), nowMs, gateway);
            case FINALIZE_AT_NEAREST_TOWN, FINAL_RETURN_TO_INSTRUCTOR ->
                    finishAtNearestTown(entry, agent, state, nowMs, gateway);
            default -> false;
        };
    }

    private static boolean runPack(AgentRuntimeEntry entry,
                                   Character agent,
                                   AgentCareerProgressionState state,
                                   String packId,
                                   AgentCareerProgressionState.Stage completedStage,
                                   long nowMs,
                                   PrimitiveCapabilityGateway gateway) {
        AgentVictoriaSharedQuestPackRuntime.Result result =
                AgentVictoriaSharedQuestPackRuntime.tick(
                        entry, agent, state, packId, nowMs, gateway);
        if (result == AgentVictoriaSharedQuestPackRuntime.Result.COMPLETE) {
            state.questPackIndex(0);
            state.stage(completedStage, nowMs + AgentVictoriaProgressionPolicy.defaultPolicy()
                    .interactionDelayMs(agent.getId(), packId.hashCode(), 3));
        }
        return result != AgentVictoriaSharedQuestPackRuntime.Result.BLOCKED;
    }

    private static boolean runRotationPack(AgentRuntimeEntry entry,
                                           Character agent,
                                           AgentCareerProgressionState state,
                                           String packId,
                                           int milestoneLevel,
                                           long nowMs,
                                           PrimitiveCapabilityGateway gateway) {
        AgentVictoriaSharedQuestPackRuntime.Result result =
                AgentVictoriaSharedQuestPackRuntime.tick(
                        entry, agent, state, packId, nowMs, gateway);
        if (result == AgentVictoriaSharedQuestPackRuntime.Result.COMPLETE) {
            state.questPackIndex(0);
            state.stage(stageAfterRotation(agent.getLevel(), milestoneLevel),
                    nowMs + AgentVictoriaProgressionPolicy.defaultPolicy()
                            .interactionDelayMs(agent.getId(), packId.hashCode(), 3));
        }
        return result != AgentVictoriaSharedQuestPackRuntime.Result.BLOCKED;
    }

    private static boolean afterHome(Character agent,
                                     AgentCareerProgressionState state,
                                     AgentCareerBuildBundle bundle,
                                     AgentVictoriaLevel15Catalog.CatchUpPlan plan,
                                     long nowMs) {
        AgentCareerProgressionState.Stage next = stageAfterHome(
                agent.getLevel(), agent.getExp(), bundle.milestoneLevel(), plan.afterHomeStrategy());
        state.questPackIndex(0);
        state.stage(next, nowMs);
        return true;
    }

    static AgentCareerProgressionState.Stage stageAfterHome(
            int currentLevel,
            int currentExp,
            int milestoneLevel,
            AgentVictoriaLevel15Catalog.AfterHomeStrategy strategy) {
        if (currentLevel >= milestoneLevel) {
            return AgentCareerProgressionState.Stage.FINALIZE_AT_NEAREST_TOWN;
        }
        if (strategy == AgentVictoriaLevel15Catalog.AfterHomeStrategy.LOCAL_GRIND
                || closeEnoughForHomeGrind(currentLevel, currentExp, milestoneLevel)) {
            return AgentCareerProgressionState.Stage.HOME_GRIND_TO_MILESTONE;
        }
        return AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK;
    }

    private static boolean closeEnoughForHomeGrind(
            int currentLevel, int currentExp, int milestoneLevel) {
        if (currentLevel != milestoneLevel - 1 || currentLevel <= 0) {
            return false;
        }
        int requiredExp = ExpTable.getExpNeededForLevel(currentLevel);
        int boundedExp = Math.max(0, Math.min(currentExp, requiredExp));
        long remainingExp = requiredExp - (long) boundedExp;
        return remainingExp * 100L <= requiredExp * (long) HOME_GRIND_MAX_REMAINING_PERCENT;
    }

    static AgentCareerProgressionState.Stage stageAfterRotation(int currentLevel, int milestoneLevel) {
        return currentLevel >= milestoneLevel
                ? AgentCareerProgressionState.Stage.FINALIZE_AT_NEAREST_TOWN
                : AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE;
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
            state.stage(AgentCareerProgressionState.Stage.FINALIZE_AT_NEAREST_TOWN, nowMs);
            return true;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, grind.huntingMapId(), gateway)) {
            return true;
        }
        gateway.grind(entry, Set.copyOf(grind.mobIds()));
        return true;
    }

    private static boolean finishAtNearestTown(AgentRuntimeEntry entry,
                                               Character agent,
                                               AgentCareerProgressionState state,
                                               long nowMs,
                                               PrimitiveCapabilityGateway gateway) {
        MapleMap currentMap = agent.getMap();
        MapleMap nearestTown = currentMap == null ? null : currentMap.getReturnMap();
        if (nearestTown != null && nearestTown.getId() != agent.getMapId()
                && AgentVictoriaRouteRuntime.travel(entry, agent, nearestTown.getId(), gateway)) {
            return true;
        }
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
