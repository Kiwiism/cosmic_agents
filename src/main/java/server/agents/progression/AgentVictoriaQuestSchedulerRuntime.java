package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.capabilities.contracts.AgentProcurementMethod;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.supplies.AgentResourcePlanningState;
import server.agents.events.AgentDomainEvent;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.integration.AgentNavigationReadinessRuntime;
import server.agents.progression.questwork.AgentQuestAttemptBudgetPolicy;
import server.agents.progression.questwork.AgentQuestAttemptObservation;
import server.agents.progression.questwork.AgentQuestStruggleAssessmentFactory;
import server.agents.progression.questwork.AgentQuestStruggleAdvisor;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.runtime.decision.AgentDecisionAdvisoryService;
import server.agents.runtime.decision.AgentDecisionRecommendation;
import server.agents.runtime.decision.AgentRecommendedAction;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative generic quest compiler for local Victoria hunting and interaction quests. */
final class AgentVictoriaQuestSchedulerRuntime {
    private static final int INTERACTION_DISTANCE_PX = config.AgentTuning.intValue("server.agents.progression.AgentVictoriaQuestSchedulerRuntime.INTERACTION_DISTANCE_PX");
    private static final long ATTEMPT_ASSESSMENT_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaQuestSchedulerRuntime.ATTEMPT_ASSESSMENT_INTERVAL_MS");
    private static final long NAVIGATION_WARMUP_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaQuestSchedulerRuntime.NAVIGATION_WARMUP_GRACE_MS");
    private static final AgentDecisionAdvisoryService QUEST_ADVISOR =
            new AgentDecisionAdvisoryService(new AgentQuestStruggleAdvisor());
    private static final AgentQuestStruggleAssessmentFactory QUEST_ASSESSMENTS =
            new AgentQuestStruggleAssessmentFactory();

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
            AgentQuestReturnScrollPolicy.clear(entry);
            state.completeAndDefer(agent.getLevel());
            return false;
        }
        if (status == QuestStatus.Status.STARTED.getId()
                && (state.stage() == AgentVictoriaQuestSchedulerState.Stage.TRAVEL_TO_START
                || state.stage() == AgentVictoriaQuestSchedulerState.Stage.START)) {
            state.stage(AgentVictoriaQuestSchedulerState.Stage.HUNT);
        }
        ensureAttemptStarted(agent, quest, state, gateway, nowMs);
        return switch (state.stage()) {
            case TRAVEL_TO_START -> travel(entry, agent, state.startMapId(), gateway, nowMs,
                    () -> state.stage(AgentVictoriaQuestSchedulerState.Stage.START), state);
            case START -> interact(entry, agent, quest.startNpcId(), nowMs, gateway,
                    () -> gateway.startQuest(agent, quest.questId(), quest.startNpcId()),
                    () -> state.stage(AgentVictoriaQuestSchedulerState.Stage.HUNT), state, 1);
            case HUNT -> hunt(entry, agent, quest, state, gateway, nowMs);
            case TRAVEL_TO_COMPLETE -> returnToComplete(
                    entry, agent, state.completeMapId(), gateway, nowMs,
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
        int requestedQuestId = state.requestedQuestId();
        if (requestedQuestId > 0) {
            eligible = eligible.stream()
                    .filter(quest -> quest.questId() == requestedQuestId)
                    .toList();
        }
        AgentVictoriaQuestRuntimeCatalog.Entry started = eligible.stream()
                .filter(quest -> gateway.questStatus(agent, quest.questId())
                        == QuestStatus.Status.STARTED.getId())
                .filter(quest -> !state.failed(quest.questId()))
                .filter(quest -> !state.suspendedAtLevel(quest.questId(), agent.getLevel()))
                .findFirst().orElse(null);
        if (started != null) {
            int completionMap = firstReachable(agent.getMapId(), started.completeMapIds());
            if (completionMap > 0) {
                state.begin(started.questId(), agent.getMapId(), completionMap, true);
                return true;
            }
        }
        if (state.deferUntilLevel() == agent.getLevel() && requestedQuestId == 0) {
            return false;
        }
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);
        int decision = Math.floorMod(agent.getId() * 31 + agent.getLevel() * 17, 100);
        int questDecisionPercent = AgentProgressionDecisionPolicy.questDecisionPercent(profile,
                AgentVictoriaProgressionPolicy.defaultPolicy().questDecisionPercent());
        if (requestedQuestId == 0 && decision >= questDecisionPercent) {
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
            if (requestedQuestId > 0) {
                state.failRequestedAndDefer(agent.getLevel());
            } else {
                state.defer(agent.getLevel());
            }
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
            AgentHuntRecoveryRuntime.clear(entry, "scheduler:" + quest.questId() + ":"
                    + objectives.get(objectiveIndex).objectiveId());
            objectiveIndex++;
            state.objectiveIndex(objectiveIndex);
        }
        if (objectiveIndex >= objectives.size()) {
            state.stage(AgentVictoriaQuestSchedulerState.Stage.TRAVEL_TO_COMPLETE);
            return true;
        }
        AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective = objectives.get(objectiveIndex);
        int currentCount = objective.type().contains("collect")
                ? gateway.itemCount(agent, objective.targetId())
                : gateway.questProgress(agent, quest.questId(), objective.targetId());
        String huntKey = "scheduler:" + quest.questId() + ":" + objective.objectiveId();
        state.observeAttempt(agent.getMapId(), currentCount, nowMs);
        AgentDecisionRecommendation recommendation = assessAttempt(
                entry, agent, quest, state, huntKey, currentCount, nowMs);
        if (recommendation != null && recommendation.action() != AgentRecommendedAction.CONTINUE) {
            publishDecision(entry, agent, quest, state, recommendation, nowMs);
            if (recommendation.action() == AgentRecommendedAction.REPLAN_CURRENT) {
                state.recordRetry();
                state.huntMapId(0);
                gateway.stop(entry);
                return true;
            }
            if (recommendation.action() == AgentRecommendedAction.RESUPPLY
                    && criticalShopMaintenanceAvailable(entry)) {
                gateway.stop(entry);
                return false;
            }
            if (recommendation.action() == AgentRecommendedAction.RESUPPLY
                    || recommendation.action() == AgentRecommendedAction.SUSPEND
                    || recommendation.action() == AgentRecommendedAction.ABANDON_OBJECTIVE
                    || recommendation.action() == AgentRecommendedAction.SAFE_FALLBACK) {
                gateway.stop(entry);
                state.suspendAndDefer(agent.getLevel());
                return false;
            }
        }
        AgentVictoriaQuestRuntimeCatalog.HuntMap huntMap = state.huntMapId() == 0 ? null
                : objective.huntMaps().stream()
                .filter(map -> map.mapId() == state.huntMapId())
                .filter(map -> AgentVictoriaTrainingRouteCatalog.canRoute(agent.getMapId(), map.mapId()))
                .findFirst().orElse(null);
        if (huntMap == null) {
            state.huntMapId(0);
            boolean recovering = AgentHuntRecoveryRuntime.fallbackActive(
                    entry, huntKey, currentCount, nowMs);
            AgentAdaptiveQuestHuntSelector.Selection selection =
                    AgentAdaptiveQuestHuntSelector.defaultSelector()
                    .select(new AgentHuntSelectionRequest(
                            entry, agent, huntKey,
                            List.of(new AgentHuntSelectionRequest.ObjectiveDemand(
                                    quest.questId(), objective.objectiveId(), objective.type(),
                                    objective.targetId(), objective.requiredCount(), currentCount,
                                    Set.copyOf(objective.sourceMobIds()))),
                            objective.huntMaps(), AgentHuntRecoveryRuntime.failedMaps(
                                    entry, huntKey, currentCount, nowMs), false,
                            recovering ? AgentHuntSelectionRequest.Reason.EXHAUSTION_FALLBACK
                                    : AgentHuntSelectionRequest.Reason.NORMAL,
                            nowMs)).orElse(null);
            huntMap = selection == null ? null : selection.map();
            if (huntMap == null) {
                state.failAndDefer(agent.getLevel());
                return false;
            }
            state.huntMapId(huntMap.mapId());
            publishHuntMapSelection(entry, agent, quest, objective, huntMap,
                    selection, recovering, currentCount, nowMs);
        }
        if (agent.getMapId() != huntMap.mapId()
                && AgentQuestReturnScrollPolicy.prepare(
                entry, agent, "scheduler:quest:" + quest.questId()
                        + ":objective:" + objective.objectiveId(),
                huntMap.mapId(), state.completeMapId(), nowMs, gateway)
                == AgentQuestReturnScrollPolicy.Preparation.WAITING) {
            return true;
        }
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, huntMap.mapId(), gateway, nowMs);
        if (outcome.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            state.recordNavigationFailure();
            state.huntMapId(0);
            return true;
        }
        if (outcome.status() != AgentVictoriaRouteRuntime.Status.ARRIVED) {
            return true;
        }
        AgentHuntRecoveryRuntime.Observation observation = AgentHuntRecoveryRuntime.observe(
                entry, huntKey, agent.getMapId(), currentCount,
                gateway.liveMonsterCount(agent, Set.copyOf(huntMap.targetMobIds())), false, nowMs);
        if (observation == AgentHuntRecoveryRuntime.Observation.RESELECT) {
            state.recordRetry();
            AgentHuntRecoveryRuntime.failMaps(entry, huntKey, currentCount,
                    Set.of(huntMap.mapId()), nowMs);
            state.huntMapId(0);
            gateway.stop(entry);
            return true;
        }
        AgentQuestHuntingBridge.engage(entry, agent, gateway, huntKey,
                AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                Set.copyOf(huntMap.targetMobIds()), Set.of(), nowMs);
        return true;
    }

    private static void ensureAttemptStarted(
            Character agent,
            AgentVictoriaQuestRuntimeCatalog.Entry quest,
            AgentVictoriaQuestSchedulerState state,
            PrimitiveCapabilityGateway gateway,
            long nowMs) {
        if (state.attemptStartedAtMs() > 0L) {
            return;
        }
        int progress = quest.huntingObjectives().stream().mapToInt(objective ->
                objective.type().contains("collect")
                        ? gateway.itemCount(agent, objective.targetId())
                        : gateway.questProgress(agent, quest.questId(), objective.targetId())).sum();
        state.beginAttempt(nowMs, agent.getMapId(), progress, resourceUnits(agent),
                AgentQuestAttemptBudgetPolicy.budgetForLevel(agent.getLevel()));
    }

    private static AgentDecisionRecommendation assessAttempt(
            AgentRuntimeEntry entry,
            Character agent,
            AgentVictoriaQuestRuntimeCatalog.Entry quest,
            AgentVictoriaQuestSchedulerState state,
            String huntKey,
            int currentCount,
            long nowMs) {
        if (state.attemptStartedAtMs() <= 0L || nowMs < state.nextAssessmentAtMs()) {
            return null;
        }
        state.assessedAt(nowMs + ATTEMPT_ASSESSMENT_INTERVAL_MS);
        int consumed = Math.max(0, state.initialResourceUnits() - resourceUnits(agent));
        AgentQuestAttemptObservation observation = new AgentQuestAttemptObservation(
                Integer.toString(agent.getId()), "quest:" + quest.questId(), quest.questId(),
                state.attemptStartedAtMs(), nowMs, state.lastObjectiveProgressAtMs(),
                AgentHuntRecoveryRuntime.lastRelevantDamageAt(
                        entry, huntKey, currentCount, nowMs),
                state.lastNavigationProgressAtMs(), legitimateWaitUntil(entry, agent, state, nowMs),
                state.navigationFailureCount(), state.retryCount(), consumed,
                state.resourceBudget());
        AgentDecisionRecommendation recommendation = QUEST_ADVISOR.evaluate(
                QUEST_ASSESSMENTS.create(observation));
        QUEST_ADVISOR.record(entry, recommendation);
        return recommendation;
    }

    private static long legitimateWaitUntil(
            AgentRuntimeEntry entry,
            Character agent,
            AgentVictoriaQuestSchedulerState state,
            long nowMs) {
        long waitUntil = Math.max(nowMs, state.nextActionAtMs());
        if (AgentNavigationReadinessRuntime.warmupPending(entry, agent.getMapId())
                && nowMs - state.attemptStartedAtMs() < NAVIGATION_WARMUP_GRACE_MS) {
            waitUntil = Math.max(waitUntil, nowMs + ATTEMPT_ASSESSMENT_INTERVAL_MS);
        }
        return waitUntil;
    }

    private static boolean criticalShopMaintenanceAvailable(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentResourcePlanningState.STATE_KEY)
                .procurementSnapshot().values().stream()
                .anyMatch(request -> request.urgency().ordinal()
                        >= AgentSupplyUrgency.CRITICAL.ordinal()
                        && request.permittedMethods().contains(AgentProcurementMethod.NPC_SHOP));
    }

    private static int resourceUnits(Character agent) {
        int[] potions = AgentPotionService.countPotions(agent);
        return Math.max(0, potions[0]) + Math.max(0, potions[1]);
    }

    private static void publishDecision(
            AgentRuntimeEntry entry,
            Character agent,
            AgentVictoriaQuestRuntimeCatalog.Entry quest,
            AgentVictoriaQuestSchedulerState state,
            AgentDecisionRecommendation recommendation,
            long nowMs) {
        AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(
                agent.getId(), nowMs, "progression.quest-decision",
                recommendation.correlationId(), Map.of(
                "questId", Integer.toString(quest.questId()),
                "action", recommendation.action().name(),
                "reasonCode", recommendation.reasonCode().name(),
                "reason", recommendation.explanation(),
                "stage", state.stage().name(),
                "mapId", Integer.toString(agent.getMapId()),
                "resourceBudget", Integer.toString(state.resourceBudget()))));
    }

    private static void publishHuntMapSelection(
            AgentRuntimeEntry entry,
            Character agent,
            AgentVictoriaQuestRuntimeCatalog.Entry quest,
            AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective,
            AgentVictoriaQuestRuntimeCatalog.HuntMap map,
            AgentAdaptiveQuestHuntSelector.Selection selection,
            boolean recovering,
            int currentCount,
            long nowMs) {
        String source = selection == null ? "UNKNOWN" : selection.source().name();
        String mode = selection == null ? "UNKNOWN" : selection.mode().name();
        AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(
                agent.getId(), nowMs, "progression.quest-map-selected",
                "quest-map:" + agent.getId() + ':' + quest.questId() + ':'
                        + objective.objectiveId() + ':' + nowMs,
                Map.of("questId", Integer.toString(quest.questId()),
                        "objectiveId", objective.objectiveId(),
                        "mapId", Integer.toString(map.mapId()),
                        "source", source,
                        "selectionMode", mode,
                        "recoveryFallback", Boolean.toString(recovering),
                        "currentCount", Integer.toString(currentCount),
                        "requiredCount", Integer.toString(objective.requiredCount()),
                        "reason", recovering
                                ? "spawn or progress exhaustion fallback"
                                : "ranked quest-debt hunt selection")));
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

    private static boolean returnToComplete(AgentRuntimeEntry entry,
                                            Character agent,
                                            int destinationMapId,
                                            PrimitiveCapabilityGateway gateway,
                                            long nowMs,
                                            Runnable arrived,
                                            AgentVictoriaQuestSchedulerState state) {
        if (AgentQuestReturnScrollPolicy.useForReturn(entry, agent, destinationMapId, gateway)) {
            return true;
        }
        return travel(entry, agent, destinationMapId, gateway, nowMs, arrived, state);
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
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, INTERACTION_DISTANCE_PX)) {
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
