package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative generic quest compiler for local, hunting-only Victoria quests. */
final class AgentVictoriaQuestSchedulerRuntime {
    private static final int INTERACTION_DISTANCE_PX = 100;

    private AgentVictoriaQuestSchedulerRuntime() {
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentVictoriaTrainingState training = entry.capabilityStates().require(
                AgentVictoriaTrainingState.STATE_KEY);
        if (!training.questsEnabled()) {
            return false;
        }
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        AgentVictoriaQuestRuntimeCatalogRepository repository =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository();
        if (!state.active() && !select(entry, agent, gateway, state, repository)) {
            return false;
        }
        AgentVictoriaQuestRuntimeCatalog.Entry quest = repository.find(state.questId()).orElse(null);
        if (quest == null) {
            state.failAndDefer(agent.getLevel());
            return false;
        }
        int status = gateway.questStatus(agent, quest.questId());
        if (status == QuestStatus.Status.COMPLETED.getId()) {
            state.completeAndDefer(agent.getLevel());
            return false;
        }
        if (status == QuestStatus.Status.STARTED.getId()
                && (state.stage() == AgentVictoriaQuestSchedulerState.Stage.TRAVEL_TO_START
                || state.stage() == AgentVictoriaQuestSchedulerState.Stage.START)) {
            state.stage(AgentVictoriaQuestSchedulerState.Stage.HUNT);
        }
        return switch (state.stage()) {
            case TRAVEL_TO_START -> travel(entry, agent, state.startMapId(), gateway, nowMs,
                    () -> state.stage(AgentVictoriaQuestSchedulerState.Stage.START), state);
            case START -> interact(entry, agent, quest.startNpcId(), nowMs, gateway,
                    () -> gateway.startQuest(agent, quest.questId(), quest.startNpcId()),
                    () -> state.stage(AgentVictoriaQuestSchedulerState.Stage.HUNT), state, 1);
            case HUNT -> hunt(entry, agent, quest, state, gateway, nowMs);
            case TRAVEL_TO_COMPLETE -> travel(entry, agent, state.completeMapId(), gateway, nowMs,
                    () -> state.stage(AgentVictoriaQuestSchedulerState.Stage.COMPLETE), state);
            case COMPLETE -> interact(entry, agent, quest.completeNpcId(), nowMs, gateway,
                    () -> gateway.completeQuest(agent, quest.questId(), quest.completeNpcId()),
                    () -> state.completeAndDefer(agent.getLevel()), state, 2);
            case IDLE -> false;
        };
    }

    private static boolean select(AgentRuntimeEntry entry,
                                  Character agent,
                                  PrimitiveCapabilityGateway gateway,
                                  AgentVictoriaQuestSchedulerState state,
                                  AgentVictoriaQuestRuntimeCatalogRepository repository) {
        List<AgentVictoriaQuestRuntimeCatalog.Entry> eligible = repository.eligibleAtLevel(agent.getLevel());
        AgentVictoriaQuestRuntimeCatalog.Entry started = eligible.stream()
                .filter(quest -> gateway.questStatus(agent, quest.questId())
                        == QuestStatus.Status.STARTED.getId())
                .filter(quest -> !state.failed(quest.questId()))
                .findFirst().orElse(null);
        if (started != null) {
            int completionMap = firstReachable(agent.getMapId(), started.completeMapIds());
            if (completionMap > 0) {
                state.begin(started.questId(), agent.getMapId(), completionMap, true);
                return true;
            }
        }
        if (state.deferUntilLevel() == agent.getLevel()) {
            return false;
        }
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);
        int decision = Math.floorMod(agent.getId() * 31 + agent.getLevel() * 17, 100);
        int questDecisionPercent = AgentProgressionDecisionPolicy.questDecisionPercent(profile,
                AgentVictoriaProgressionPolicy.defaultPolicy().questDecisionPercent());
        if (decision >= questDecisionPercent) {
            state.defer(agent.getLevel());
            return false;
        }
        AgentVictoriaQuestRuntimeCatalog.Entry selected = eligible.stream()
                .filter(quest -> gateway.questStatus(agent, quest.questId())
                        == QuestStatus.Status.NOT_STARTED.getId())
                .filter(quest -> !state.failed(quest.questId()))
                .filter(quest -> gateway.canStartQuest(agent, quest.questId(), quest.startNpcId()))
                .filter(quest -> firstReachable(agent.getMapId(), quest.startMapIds()) > 0)
                .sorted(Comparator
                        .comparingLong((AgentVictoriaQuestRuntimeCatalog.Entry quest) ->
                                AgentProgressionDecisionPolicy.questScore(profile, agent.getId(),
                                        agent.getLevel(), agent.getMapId(), quest)).reversed()
                        .thenComparingInt(AgentVictoriaQuestRuntimeCatalog.Entry::questId))
                .findFirst().orElse(null);
        if (selected == null) {
            state.defer(agent.getLevel());
            return false;
        }
        int startMap = firstReachable(agent.getMapId(), selected.startMapIds());
        int completeMap = firstReachable(startMap, selected.completeMapIds());
        if (completeMap <= 0) {
            state.defer(agent.getLevel());
            return false;
        }
        state.begin(selected.questId(), startMap, completeMap, false);
        return true;
    }

    private static boolean hunt(AgentRuntimeEntry entry,
                                Character agent,
                                AgentVictoriaQuestRuntimeCatalog.Entry quest,
                                AgentVictoriaQuestSchedulerState state,
                                PrimitiveCapabilityGateway gateway,
                                long nowMs) {
        if (gateway.canCompleteQuest(agent, quest.questId(), quest.completeNpcId())) {
            state.stage(AgentVictoriaQuestSchedulerState.Stage.TRAVEL_TO_COMPLETE);
            return true;
        }
        List<AgentVictoriaQuestRuntimeCatalog.HuntingObjective> objectives = quest.huntingObjectives();
        int objectiveIndex = state.objectiveIndex();
        while (objectiveIndex < objectives.size()
                && complete(agent, quest.questId(), objectives.get(objectiveIndex), gateway)) {
            objectiveIndex++;
            state.objectiveIndex(objectiveIndex);
        }
        if (objectiveIndex >= objectives.size()) {
            state.stage(AgentVictoriaQuestSchedulerState.Stage.TRAVEL_TO_COMPLETE);
            return true;
        }
        AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective = objectives.get(objectiveIndex);
        AgentVictoriaQuestRuntimeCatalog.HuntMap huntMap = state.huntMapId() == 0 ? null
                : objective.huntMaps().stream()
                .filter(map -> map.mapId() == state.huntMapId())
                .filter(map -> AgentVictoriaTrainingRouteCatalog.canRoute(agent.getMapId(), map.mapId()))
                .findFirst().orElse(null);
        if (huntMap == null) {
            state.huntMapId(0);
            huntMap = selectHuntMap(entry, agent, objective.huntMaps());
            if (huntMap == null) {
                state.failAndDefer(agent.getLevel());
                return false;
            }
            state.huntMapId(huntMap.mapId());
        }
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, huntMap.mapId(), gateway, nowMs);
        if (outcome.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            state.huntMapId(0);
            return true;
        }
        if (outcome.status() != AgentVictoriaRouteRuntime.Status.ARRIVED) {
            return true;
        }
        gateway.grind(entry, Set.copyOf(huntMap.targetMobIds()));
        return true;
    }

    private static AgentVictoriaQuestRuntimeCatalog.HuntMap selectHuntMap(
            AgentRuntimeEntry entry,
            Character agent,
            List<AgentVictoriaQuestRuntimeCatalog.HuntMap> candidates) {
        Set<Integer> eligibleIds = new LinkedHashSet<>();
        for (AgentVictoriaQuestRuntimeCatalog.HuntMap map : candidates) {
            if (AgentVictoriaTrainingRouteCatalog.canRoute(agent.getMapId(), map.mapId())) {
                eligibleIds.add(map.mapId());
            }
        }
        Map<Integer, Integer> occupancy = AgentVictoriaTrainingPopulation.snapshot(agent, eligibleIds);
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);
        return candidates.stream()
                .filter(map -> eligibleIds.contains(map.mapId()))
                .filter(map -> occupancy.getOrDefault(map.mapId(), 0) < map.maximumAgents())
                .max(Comparator.comparingLong(map -> AgentProgressionDecisionPolicy.huntMapScore(
                        profile, agent.getId(), agent.getLevel(), agent.getMapId(), map,
                        occupancy.getOrDefault(map.mapId(), 0))))
                .orElse(null);
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
                                  int destinationMapId,
                                  PrimitiveCapabilityGateway gateway,
                                  long nowMs,
                                  Runnable arrived,
                                  AgentVictoriaQuestSchedulerState state) {
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, destinationMapId, gateway, nowMs);
        if (outcome.status() == AgentVictoriaRouteRuntime.Status.ARRIVED) {
            arrived.run();
        } else if (outcome.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            state.failAndDefer(agent.getLevel());
            return false;
        }
        return true;
    }

    private static boolean interact(AgentRuntimeEntry entry,
                                    Character agent,
                                    int npcId,
                                    long nowMs,
                                    PrimitiveCapabilityGateway gateway,
                                    Action action,
                                    Runnable succeeded,
                                    AgentVictoriaQuestSchedulerState state,
                                    int stageSalt) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            state.failAndDefer(agent.getLevel());
            return false;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc) > INTERACTION_DISTANCE_PX * INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            state.nextActionAtMs(0L);
            return true;
        }
        if (state.nextActionAtMs() == 0L) {
            state.nextActionAtMs(nowMs + AgentVictoriaProgressionPolicy.defaultPolicy()
                    .interactionDelayMs(agent.getId(), state.questId(), stageSalt));
            gateway.stop(entry);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        if (action.run()) {
            succeeded.run();
        } else {
            state.failAndDefer(agent.getLevel());
        }
        return true;
    }

    private static int firstReachable(int sourceMapId, List<Integer> candidates) {
        return candidates.stream()
                .filter(mapId -> AgentVictoriaTrainingRouteCatalog.canRoute(sourceMapId, mapId))
                .findFirst().orElse(-1);
    }

    @FunctionalInterface
    private interface Action {
        boolean run();
    }
}
