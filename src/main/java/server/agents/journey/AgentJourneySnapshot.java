package server.agents.journey;

import client.Character;
import client.QuestStatus;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.plans.AgentPlanSessionState;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Read-only reconciliation snapshot used to fill evidence gaps without mutating Cosmic. */
public record AgentJourneySnapshot(
        int schemaVersion,
        long capturedAtMs,
        int agentId,
        String agentName,
        int level,
        int experience,
        int jobId,
        int mapId,
        int x,
        int y,
        int hp,
        int mp,
        int mesos,
        String planId,
        String planStatus,
        int planStepIndex,
        int planStepAttempt,
        String objectiveId,
        String objectiveType,
        String careerStage,
        Map<Integer, Integer> inventory,
        Map<Integer, Map<Integer, String>> activeQuestProgress,
        Set<Integer> completedQuestIds) {

    public static AgentJourneySnapshot capture(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentPlanSessionState plan = entry.capabilityStates().find(AgentPlanSessionState.STATE_KEY)
                .orElse(null);
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        AgentCareerProgressionState career = entry.capabilityStates()
                .find(AgentCareerProgressionState.STATE_KEY).orElse(null);
        Point position = agent.getPosition();
        Map<Integer, Integer> inventory = new TreeMap<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD) {
                continue;
            }
            for (Item item : agent.getInventory(type).list()) {
                inventory.merge(item.getItemId(), Math.max(0, (int) item.getQuantity()), Integer::sum);
            }
        }
        Map<Integer, Map<Integer, String>> quests = new LinkedHashMap<>();
        for (QuestStatus status : agent.getStartedQuests()) {
            quests.put((int) status.getQuestID(),
                    Map.copyOf(new TreeMap<>(status.getProgress())));
        }
        Set<Integer> completed = agent.getCompletedQuests().stream()
                .map(status -> (int) status.getQuestID())
                .collect(Collectors.toUnmodifiableSet());
        return new AgentJourneySnapshot(
                1, nowMs, agent.getId(), agent.getName(), agent.getLevel(), agent.getExp(),
                agent.getJob().getId(), agent.getMapId(),
                position == null ? 0 : position.x, position == null ? 0 : position.y,
                agent.getHp(), agent.getMp(), agent.getMeso(),
                plan == null ? "" : plan.planId(),
                plan == null ? "IDLE" : plan.status().name(),
                plan == null ? 0 : plan.stepIndex(),
                plan == null ? 0 : plan.stepAttempt(),
                objective == null ? "" : objective.objectiveId(),
                objective == null ? "" : objective.type(),
                career == null ? "" : career.stage().name(),
                Map.copyOf(inventory), Map.copyOf(quests), completed);
    }
}
