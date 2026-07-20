package server.agents.progression;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Occupancy-aware, resumable level 15-30 Victoria grinding objective. */
public final class AgentVictoriaTrainingObjectiveRuntime {
    static final String OBJECTIVE_TYPE = "progression.victoria-training";
    private static final String OBJECTIVE_PREFIX = "victoria:training:";
    private static final long NO_SELECTION_RETRY_MS = 15_000L;
    private static final long DESTINATION_RETRY_MS = 120_000L;

    private AgentVictoriaTrainingObjectiveRuntime() {
    }

    public static boolean start(AgentRuntimeEntry entry, Character agent, int targetLevel, long nowMs) {
        return start(entry, agent, targetLevel,
                AgentVictoriaProgressionPolicy.defaultPolicy().questingEnabledByDefault(), nowMs);
    }

    public static boolean start(AgentRuntimeEntry entry,
                                Character agent,
                                int targetLevel,
                                boolean questsEnabled,
                                long nowMs) {
        if (entry == null || agent == null || agent.getJob().getId() == 0
                || agent.getLevel() < 15 || targetLevel < 16 || targetLevel > 30
                || targetLevel <= agent.getLevel() || AgentObjectiveKernel.active(entry) != null) {
            return false;
        }
        String catalogId = AgentVictoriaTrainingCatalogRepository.defaultRepository()
                .catalog().catalogId();
        AgentObjectiveKernel.start(entry, new AgentObjectiveDefinition(
                objectiveId(agent.getId(), targetLevel), OBJECTIVE_TYPE, 80, Long.MAX_VALUE, 5,
                AgentObjectiveSource.OPERATOR_COMMAND, catalogId,
                agent.getId() + ":level-" + targetLevel + ":mode-"
                        + (questsEnabled ? "mixed" : "grind")), nowMs);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY)
                .start(targetLevel, questsEnabled, nowMs);
        return true;
    }

    public static boolean cancel(AgentRuntimeEntry entry, long nowMs) {
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
        if (active == null || !OBJECTIVE_TYPE.equals(active.type())) {
            return false;
        }
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).stop();
        return AgentObjectiveKernel.transition(entry, active.objectiveId(), AgentObjectiveStatus.CANCELLED,
                "Victoria training cancelled by operator", nowMs);
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        if (objective == null || !OBJECTIVE_TYPE.equals(objective.type())) {
            return false;
        }
        AgentVictoriaTrainingState state = entry.capabilityStates().require(
                AgentVictoriaTrainingState.STATE_KEY);
        if (!state.active()) {
            int target = targetLevel(objective.objectiveId());
            if (target < 16 || target > 30) {
                AgentObjectiveKernel.transition(entry, objective.objectiveId(), AgentObjectiveStatus.BLOCKED,
                        "durable Victoria objective has an invalid target level", nowMs);
                return false;
            }
            state.start(target, !objective.correlationId().endsWith(":mode-grind"), nowMs);
        }
        AgentVictoriaProgressionDiagnostics.captureIfLevelChanged(entry, agent, nowMs);
        if (agent.getLevel() >= state.targetLevel()) {
            AgentVictoriaQuestSchedulerState quests = entry.capabilityStates().require(
                    AgentVictoriaQuestSchedulerState.STATE_KEY);
            if (quests.active()
                    && AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, nowMs, gateway)) {
                return true;
            }
            gateway.stop(entry);
            state.stop();
            AgentObjectiveKernel.transition(entry, objective.objectiveId(), AgentObjectiveStatus.SUCCEEDED,
                    "target level " + agent.getLevel() + " reached", nowMs);
            return false;
        }
        if (agent.getLevel() < 15 || agent.getJob().getId() == 0) {
            return true;
        }
        if (AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, nowMs, gateway)) {
            return true;
        }

        AgentVictoriaTrainingCatalogRepository repository =
                AgentVictoriaTrainingCatalogRepository.defaultRepository();
        AgentVictoriaTrainingCatalog.TrainingMap selected = repository
                .findMap(state.selectedMapId()).orElse(null);
        if (selected == null || state.selectedAtLevel() != agent.getLevel()
                || !state.available(selected.mapId(), nowMs)) {
            if (nowMs < state.nextSelectionAtMs()) {
                return true;
            }
            selected = select(entry, agent, state, repository, nowMs).orElse(null);
            if (selected == null) {
                gateway.stop(entry);
                state.retrySelectionAt(nowMs + NO_SELECTION_RETRY_MS);
                return true;
            }
        }

        AgentVictoriaRouteRuntime.TravelOutcome travel = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, selected.mapId(), gateway, nowMs);
        if (travel.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            state.markUnavailable(selected.mapId(), nowMs + DESTINATION_RETRY_MS);
            gateway.stop(entry);
            return true;
        }
        if (travel.status() != AgentVictoriaRouteRuntime.Status.ARRIVED) {
            return true;
        }

        Set<Integer> targets = new LinkedHashSet<>();
        for (AgentVictoriaTrainingCatalog.SpawnGroup spawn : selected.spawns()) {
            if (!"hazard".equalsIgnoreCase(spawn.role())) {
                targets.add(spawn.mobId());
            }
        }
        if (targets.isEmpty()) {
            state.markUnavailable(selected.mapId(), nowMs + DESTINATION_RETRY_MS);
            return true;
        }
        gateway.grind(entry, Set.copyOf(targets));
        return true;
    }

    private static Optional<AgentVictoriaTrainingCatalog.TrainingMap> select(
            AgentRuntimeEntry entry,
            Character agent,
            AgentVictoriaTrainingState state,
            AgentVictoriaTrainingCatalogRepository repository,
            long nowMs) {
        List<AgentVictoriaTrainingCatalog.TrainingChoice> choices =
                repository.choicesForLevel(agent.getLevel());
        Set<Integer> eligible = new LinkedHashSet<>();
        for (AgentVictoriaTrainingCatalog.TrainingChoice choice : choices) {
            if (state.available(choice.mapId(), nowMs)
                    && AgentVictoriaTrainingRouteCatalog.canRoute(agent.getMapId(), choice.mapId())) {
                eligible.add(choice.mapId());
            }
        }
        Map<Integer, Integer> occupancy = AgentVictoriaTrainingPopulation.snapshot(agent, eligible);
        AgentVictoriaTrainingMapSelector selector = new AgentVictoriaTrainingMapSelector(repository);
        Optional<AgentVictoriaTrainingMapSelector.Selection> selection = selector.select(
                agent.getLevel(), agent.getMapId(), occupancy, eligible);
        selection.ifPresent(value -> state.selected(value.map().mapId(), agent.getLevel(),
                value.reason() + "; occupancy=" + value.occupancy(), nowMs));
        return selection.map(AgentVictoriaTrainingMapSelector.Selection::map);
    }

    private static String objectiveId(int characterId, int targetLevel) {
        return OBJECTIVE_PREFIX + characterId + ":" + targetLevel;
    }

    private static int targetLevel(String objectiveId) {
        int separator = objectiveId == null ? -1 : objectiveId.lastIndexOf(':');
        if (separator < 0 || separator == objectiveId.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(objectiveId.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
