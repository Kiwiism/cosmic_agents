package server.agents.progression;

import client.Character;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            AgentCareerProgressionState.Stage next =
                    state.runMode() == AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP
                            ? AgentCareerProgressionState.Stage.TRAVEL_TO_INITIAL_SHOP
                            : AgentCareerProgressionState.Stage.HOME_QUEST_PACK;
            state.stage(next, nowMs);
            return true;
        }

        AgentInstructorTrainingStep step = steps.get(index);
        String objectiveKey = "instructor:" + index + ":" + gateway.questStatus(agent, step.questId());
        VictoriaFirstJobNarrator.announceObjective(agent, state, objectiveKey,
                instructorIntention(step, gateway.questStatus(agent, step.questId())));
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
        boolean killRequirementsMet = step.requiredKills().entrySet().stream()
                .allMatch(requirement -> gateway.questProgress(
                        agent, step.questId(), requirement.getKey()) >= requirement.getValue());
        if (gateway.canCompleteQuest(agent, step.questId(), bundle.instructorNpcId())
                || killRequirementsMet) {
            AgentHuntRecoveryRuntime.clear(entry, instructorObjectiveKey(step));
            VictoriaFirstJobNarrator.announceObjective(agent, state,
                    "instructor:" + index + ":return",
                    "I'm done with " + server.quest.Quest.getInstance(step.questId()).getName()
                            + " and returning to my instructor.");
            if (AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway)) {
                return true;
            }
            if (!state.ready(nowMs) || !approachNpc(entry, agent, bundle.instructorNpcId(), gateway)) {
                return true;
            }
            if (gateway.completeQuest(agent, step.questId(), bundle.instructorNpcId())) {
                state.trainingQuestIndex(index + 1);
                state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, nowMs + NPC_DELAY_MS);
            } else if (killRequirementsMet
                    && gateway.forceCompleteQuest(agent, step.questId(), bundle.instructorNpcId())) {
                // Every cataloged instructor step is WZ-audited as NPC + mob-count only.
                // This recovers old quest states whose counters are complete but whose generic
                // canComplete check does not transition, without requiring additional kills.
                state.trainingQuestIndex(index + 1);
                state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, nowMs + NPC_DELAY_MS);
            }
            return true;
        }
        AgentVictoriaLevel15Catalog.TrainingGround trainingGround = step.trainingGround();
        if (trainingGround != null) {
            String huntKey = instructorObjectiveKey(step);
            int progress = trainingProgress(agent, step, gateway);
            if (AgentHuntRecoveryRuntime.fallbackActive(entry, huntKey, progress, nowMs)) {
                return huntOutsideInstance(entry, agent, step, huntKey, progress, gateway, nowMs);
            }
            if (trainingGround.instanceMapIds().contains(agent.getMapId())) {
                AgentHuntRecoveryRuntime.Observation observation =
                        AgentHuntRecoveryRuntime.observe(entry, huntKey, agent.getMapId(), progress,
                                gateway.liveMonsterCount(agent, step.mobIds()), true, nowMs);
                if (observation == AgentHuntRecoveryRuntime.Observation.REENTER_INSTANCE) {
                    gateway.stop(entry);
                    AgentVictoriaRouteRuntime.travel(
                            entry, agent, trainingGround.entranceMapId(), gateway);
                    return true;
                }
                if (observation == AgentHuntRecoveryRuntime.Observation.RESELECT) {
                    Set<Integer> failedInstanceFamily = new java.util.LinkedHashSet<>(
                            trainingGround.instanceMapIds());
                    failedInstanceFamily.add(trainingGround.entranceMapId());
                    AgentHuntRecoveryRuntime.failMaps(entry, huntKey, progress,
                            Set.copyOf(failedInstanceFamily), nowMs);
                    gateway.stop(entry);
                    return true;
                }
                grindWithLocalIncidental(entry, agent, step.mobIds(), gateway);
                return true;
            }
            if (AgentVictoriaRouteRuntime.travel(
                    entry, agent, trainingGround.entranceMapId(), gateway)) {
                return true;
            }
            if (!approachNpc(entry, agent, trainingGround.entranceNpcId(), gateway)) {
                return true;
            }
            int instanceSelection = Math.floorMod(
                    agent.getId() + (int) (nowMs / Math.max(1L, NPC_DELAY_MS)),
                    trainingGround.instanceMapIds().size());
            if (!gateway.runNpcScript(agent, trainingGround.entranceNpcId(), instanceSelection)) {
                state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING,
                        nowMs + NPC_DELAY_MS);
            }
            return true;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, step.huntingMapId(), gateway)) {
            return true;
        }
        grindWithLocalIncidental(entry, agent, step.mobIds(), gateway);
        return true;
    }

    private static boolean huntOutsideInstance(
            AgentRuntimeEntry entry,
            Character agent,
            AgentInstructorTrainingStep step,
            String huntKey,
            int progress,
            PrimitiveCapabilityGateway gateway,
            long nowMs) {
        List<AgentHuntSelectionRequest.ObjectiveDemand> demands = new ArrayList<>();
        for (var requirement : step.requiredKills().entrySet()) {
            int current = gateway.questProgress(agent, step.questId(), requirement.getKey());
            if (current >= requirement.getValue()) {
                continue;
            }
            demands.add(new AgentHuntSelectionRequest.ObjectiveDemand(
                    step.questId(), "instructor:" + step.questId() + ":" + requirement.getKey(),
                    "kill-mob", requirement.getKey(), requirement.getValue(), current,
                    Set.of(requirement.getKey())));
        }
        if (demands.isEmpty()) {
            return true;
        }
        AgentVictoriaQuestRuntimeCatalog.HuntMap huntMap =
                AgentAdaptiveQuestHuntSelector.defaultSelector()
                        .select(new AgentHuntSelectionRequest(
                                entry, agent, huntKey, demands, List.of(),
                                AgentHuntRecoveryRuntime.failedMaps(
                                        entry, huntKey, progress, nowMs),
                                true, AgentHuntSelectionRequest.Reason.EXHAUSTION_FALLBACK, nowMs))
                        .map(AgentAdaptiveQuestHuntSelector.Selection::map)
                        .orElse(null);
        if (huntMap == null) {
            gateway.stop(entry);
            return true;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, huntMap.mapId(), gateway)) {
            return true;
        }
        AgentHuntRecoveryRuntime.Observation observation = AgentHuntRecoveryRuntime.observe(
                entry, huntKey, agent.getMapId(), progress,
                gateway.liveMonsterCount(agent, Set.copyOf(huntMap.targetMobIds())), false, nowMs);
        if (observation == AgentHuntRecoveryRuntime.Observation.RESELECT) {
            AgentHuntRecoveryRuntime.failMaps(entry, huntKey, progress,
                    Set.of(huntMap.mapId()), nowMs);
            gateway.stop(entry);
            return true;
        }
        grindWithLocalIncidental(
                entry, agent, Set.copyOf(huntMap.targetMobIds()), gateway);
        return true;
    }

    private static void grindWithLocalIncidental(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  Set<Integer> requiredMobIds,
                                                  PrimitiveCapabilityGateway gateway) {
        Set<Integer> incidentals = AgentInstructorCombatPolicy.localIncidentalMobIds(
                requiredMobIds,
                gateway.configuredMonsterSpawnCounts(agent),
                gateway.liveMonsterCounts(agent));
        if (incidentals.isEmpty()) {
            gateway.grind(entry, requiredMobIds);
            return;
        }
        gateway.grind(entry, requiredMobIds, incidentals);
    }

    private static String instructorObjectiveKey(AgentInstructorTrainingStep step) {
        return "instructor:" + step.questId();
    }

    private static int trainingProgress(
            Character agent,
            AgentInstructorTrainingStep step,
            PrimitiveCapabilityGateway gateway) {
        return step.requiredKills().entrySet().stream()
                .mapToInt(requirement -> Math.min(requirement.getValue(),
                        gateway.questProgress(agent, step.questId(), requirement.getKey())))
                .sum();
    }

    private static String instructorIntention(AgentInstructorTrainingStep step, int questStatus) {
        if (questStatus == 0) {
            return "I'm going to ask my instructor about "
                    + server.quest.Quest.getInstance(step.questId()).getName() + ".";
        }
        String requirements = step.requiredKills().entrySet().stream()
                .map(requirement -> requirement.getValue() + " "
                        + mobName(requirement.getKey()))
                .collect(Collectors.joining(" and "));
        return "I'm going to defeat " + requirements + " for "
                + server.quest.Quest.getInstance(step.questId()).getName() + ".";
    }

    private static String mobName(int mobId) {
        return switch (mobId) {
            case 100_100 -> "Snails";
            case 100_101 -> "Blue Snails";
            case 120_100 -> "Shrooms";
            case 130_100 -> "Stumps";
            case 210_100 -> "Slimes";
            case 1_120_100 -> "Octopuses";
            default -> "target monsters";
        };
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
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, NPC_DISTANCE_PX)) {
            gateway.navigate(entry, npc, true);
            return false;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        return true;
    }
}
