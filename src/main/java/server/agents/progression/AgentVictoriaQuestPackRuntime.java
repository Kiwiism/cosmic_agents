package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;
import server.agents.capabilities.inventory.demand.AgentQuestItemDemandRuntime;

import java.awt.Point;
import java.util.List;
import java.util.Set;

/** Executes one configured quest pack in order and reconciles progress from live quest truth. */
final class AgentVictoriaQuestPackRuntime {
    private static final int INTERACTION_DISTANCE_PX = config.AgentTuning.intValue("server.agents.progression.AgentVictoriaQuestPackRuntime.INTERACTION_DISTANCE_PX");

    enum Result {
        RUNNING,
        COMPLETE,
        BLOCKED
    }

    private AgentVictoriaQuestPackRuntime() {
    }

    static Result tick(AgentRuntimeEntry entry,
                       Character agent,
                       AgentVictoriaLevel15Catalog.QuestPack pack,
                       long nowMs,
                       PrimitiveCapabilityGateway gateway) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        int index = reconcileCompleted(state, agent, pack, gateway);
        Set<Integer> committedQuestIds = Set.copyOf(
                pack.questIds().subList(Math.min(index, pack.questIds().size()),
                        pack.questIds().size()));
        Set<Integer> plannedJobs = state.bundle() == null
                ? Set.of() : Set.of(state.bundle().firstJobId());
        AgentQuestItemDemandRuntime.refreshReservations(
                entry, agent, committedQuestIds, plannedJobs, nowMs);
        if (index >= pack.questIds().size()) {
            return Result.COMPLETE;
        }
        QuestPlan quest = resolve(pack.questIds().get(index), agent.getMapId());
        if (quest == null) {
            return block(entry, state, "quest pack " + pack.packId()
                    + " has no reachable runtime plan for quest " + pack.questIds().get(index), nowMs);
        }
        int status = gateway.questStatus(agent, quest.questId());
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            AgentQuestReturnScrollPolicy.clear(entry);
            if (!travel(entry, agent, quest.startMapId(), state, nowMs, gateway)) {
                return state.stage() == AgentCareerProgressionState.Stage.BLOCKED
                        ? Result.BLOCKED : Result.RUNNING;
            }
            return interact(entry, agent, quest.startNpcId(), nowMs, gateway,
                    () -> gateway.canStartQuest(agent, quest.questId(), quest.startNpcId())
                            && gateway.startQuest(agent, quest.questId(), quest.startNpcId()),
                    () -> state.stage(state.stage(), nextInteractionAt(agent, quest.questId(), 1, nowMs)),
                    state, "start quest " + quest.questId());
        }
        if (gateway.canCompleteQuest(agent, quest.questId(), quest.completeNpcId())) {
            if (AgentQuestReturnScrollPolicy.useForReturn(
                    entry, agent, quest.completeMapId(), gateway)) {
                return Result.RUNNING;
            }
            if (!travel(entry, agent, quest.completeMapId(), state, nowMs, gateway)) {
                return state.stage() == AgentCareerProgressionState.Stage.BLOCKED
                        ? Result.BLOCKED : Result.RUNNING;
            }
            return interact(entry, agent, quest.completeNpcId(), nowMs, gateway,
                    () -> gateway.completeQuest(agent, quest.questId(), quest.completeNpcId()),
                    () -> {
                        AgentQuestReturnScrollPolicy.clear(entry);
                        state.questPackIndex(index + 1);
                        state.stage(state.stage(), nextInteractionAt(agent, quest.questId(), 2, nowMs));
                    }, state, "complete quest " + quest.questId());
        }
        if (quest.objectives().isEmpty()) {
            gateway.stop(entry);
            return Result.RUNNING;
        }
        AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective = quest.objectives().stream()
                .filter(candidate -> {
                    boolean completed = complete(agent, quest.questId(), candidate, gateway);
                    if (completed) {
                        AgentHuntRecoveryRuntime.clear(entry, "pack:" + pack.packId() + ":"
                                + quest.questId() + ":" + candidate.objectiveId());
                    }
                    return !completed;
                })
                .findFirst().orElse(null);
        if (objective == null) {
            gateway.stop(entry);
            return Result.RUNNING;
        }
        int currentCount = objective.type().contains("collect")
                ? gateway.itemCount(agent, objective.targetId())
                : gateway.questProgress(agent, quest.questId(), objective.targetId());
        String huntKey = "pack:" + pack.packId() + ":" + quest.questId()
                + ":" + objective.objectiveId();
        boolean recovering = AgentHuntRecoveryRuntime.fallbackActive(
                entry, huntKey, currentCount, nowMs);
        AgentVictoriaQuestRuntimeCatalog.HuntMap huntMap =
                AgentAdaptiveQuestHuntSelector.defaultSelector()
                        .select(new AgentHuntSelectionRequest(
                                entry, agent, huntKey,
                                List.of(new AgentHuntSelectionRequest.ObjectiveDemand(
                                        quest.questId(), objective.objectiveId(), objective.type(),
                                        objective.targetId(), objective.requiredCount(), currentCount,
                                        Set.copyOf(objective.sourceMobIds()))),
                                objective.huntMaps(), AgentHuntRecoveryRuntime.failedMaps(
                                        entry, huntKey, currentCount, nowMs), true,
                                recovering
                                        ? AgentHuntSelectionRequest.Reason.EXHAUSTION_FALLBACK
                                        : AgentHuntSelectionRequest.Reason.NORMAL,
                                nowMs))
                        .map(AgentAdaptiveQuestHuntSelector.Selection::map)
                        .orElse(null);
        if (huntMap == null) {
            return block(entry, state, "quest " + quest.questId()
                    + " has no reachable hunt map", nowMs);
        }
        if (agent.getMapId() != huntMap.mapId()
                && AgentQuestReturnScrollPolicy.prepare(
                entry, agent, "pack:" + pack.packId() + ":quest:" + quest.questId()
                        + ":objective:" + objective.objectiveId(),
                huntMap.mapId(), quest.completeMapId(), nowMs, gateway)
                == AgentQuestReturnScrollPolicy.Preparation.WAITING) {
            return Result.RUNNING;
        }
        if (!travel(entry, agent, huntMap.mapId(), state, nowMs, gateway)) {
            return state.stage() == AgentCareerProgressionState.Stage.BLOCKED
                    ? Result.BLOCKED : Result.RUNNING;
        }
        AgentHuntRecoveryRuntime.Observation observation = AgentHuntRecoveryRuntime.observe(
                entry, huntKey, agent.getMapId(), currentCount,
                gateway.liveMonsterCount(agent, Set.copyOf(huntMap.targetMobIds())), false, nowMs);
        if (observation == AgentHuntRecoveryRuntime.Observation.RESELECT) {
            AgentHuntRecoveryRuntime.failMaps(entry, huntKey, currentCount,
                    Set.of(huntMap.mapId()), nowMs);
            gateway.stop(entry);
            return Result.RUNNING;
        }
        AgentQuestHuntingBridge.engage(entry, agent, gateway, huntKey,
                AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                Set.copyOf(huntMap.targetMobIds()), Set.of(), nowMs);
        return Result.RUNNING;
    }

    private static int reconcileCompleted(AgentCareerProgressionState state,
                                          Character agent,
                                          AgentVictoriaLevel15Catalog.QuestPack pack,
                                          PrimitiveCapabilityGateway gateway) {
        int index = state.questPackIndex();
        while (index < pack.questIds().size()
                && gateway.questStatus(agent, pack.questIds().get(index))
                == QuestStatus.Status.COMPLETED.getId()) {
            index++;
        }
        state.questPackIndex(index);
        return index;
    }

    private static QuestPlan resolve(int questId, int currentMapId) {
        AgentVictoriaQuestRuntimeCatalog.Entry hunting =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().find(questId).orElse(null);
        if (hunting != null) {
            int startMapId = firstReachable(currentMapId, hunting.startMapIds());
            int completeMapId = firstReachable(startMapId, hunting.completeMapIds());
            if (startMapId <= 0 || completeMapId <= 0) {
                return null;
            }
            return new QuestPlan(hunting.questId(), hunting.startNpcId(), startMapId,
                    hunting.completeNpcId(), completeMapId, hunting.huntingObjectives());
        }
        AgentVictoriaLevel15Catalog.InteractionQuest interaction =
                AgentVictoriaLevel15CatalogRepository.defaultRepository()
                        .interactionQuest(questId).orElse(null);
        return interaction == null ? null : new QuestPlan(interaction.questId(), interaction.startNpcId(),
                interaction.startMapId(), interaction.completeNpcId(), interaction.completeMapId(), List.of());
    }

    private static int firstReachable(int sourceMapId, List<Integer> candidates) {
        if (sourceMapId <= 0) {
            return -1;
        }
        return candidates.stream()
                .filter(mapId -> AgentVictoriaTrainingRouteCatalog.canRoute(sourceMapId, mapId))
                .findFirst().orElse(-1);
    }

    private static boolean complete(Character agent,
                                    int questId,
                                    AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective,
                                    PrimitiveCapabilityGateway gateway) {
        if (objective.type().contains("collect")) {
            return gateway.itemCount(agent, objective.targetId()) >= objective.requiredCount();
        }
        return gateway.questProgress(agent, questId, objective.targetId()) >= objective.requiredCount();
    }

    private static boolean travel(AgentRuntimeEntry entry,
                                  Character agent,
                                  int mapId,
                                  AgentCareerProgressionState state,
                                  long nowMs,
                                  PrimitiveCapabilityGateway gateway) {
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, mapId, gateway, nowMs);
        if (outcome.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            block(entry, state, "no route to quest-pack map " + mapId, nowMs);
            return false;
        }
        return outcome.status() == AgentVictoriaRouteRuntime.Status.ARRIVED;
    }

    private static Result interact(AgentRuntimeEntry entry,
                                   Character agent,
                                   int npcId,
                                   long nowMs,
                                   PrimitiveCapabilityGateway gateway,
                                   Action action,
                                   Runnable succeeded,
                                   AgentCareerProgressionState state,
                                   String description) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return block(entry, state, "cannot " + description + ": NPC " + npcId
                    + " is not present", nowMs);
        }
        if (!gateway.grounded(agent)
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, INTERACTION_DISTANCE_PX)) {
            gateway.navigate(entry, npc, true);
            return Result.RUNNING;
        }
        if (!state.ready(nowMs)) {
            gateway.stop(entry);
            return Result.RUNNING;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        if (!action.run()) {
            return block(entry, state, "failed to " + description, nowMs);
        }
        succeeded.run();
        return Result.RUNNING;
    }

    private static long nextInteractionAt(Character agent, int questId, int salt, long nowMs) {
        return nowMs + AgentVictoriaProgressionPolicy.defaultPolicy()
                .interactionDelayMs(agent.getId(), questId, salt);
    }

    private static Result block(AgentRuntimeEntry entry,
                                AgentCareerProgressionState state,
                                String reason,
                                long nowMs) {
        state.block(reason);
        AgentCareerObjectiveRuntime.block(entry, reason, nowMs);
        return Result.BLOCKED;
    }

    private record QuestPlan(
            int questId,
            int startNpcId,
            int startMapId,
            int completeNpcId,
            int completeMapId,
            List<AgentVictoriaQuestRuntimeCatalog.HuntingObjective> objectives) {
    }

    @FunctionalInterface
    private interface Action {
        boolean run();
    }
}
