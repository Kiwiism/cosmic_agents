package server.agents.capabilities.quest;

import java.util.Map;
import java.util.Set;

public record AgentQuestSnapshot(
        int level,
        int jobId,
        Map<Integer, AgentQuestStatus> questStatuses,
        Map<Integer, Integer> inventoryCounts,
        Map<Integer, Integer> mobKillCounts,
        Map<Integer, Integer> questProgressValues) {

    public AgentQuestSnapshot {
        questStatuses = questStatuses == null ? Map.of() : Map.copyOf(questStatuses);
        inventoryCounts = inventoryCounts == null ? Map.of() : Map.copyOf(inventoryCounts);
        mobKillCounts = mobKillCounts == null ? Map.of() : Map.copyOf(mobKillCounts);
        questProgressValues = questProgressValues == null ? Map.of() : Map.copyOf(questProgressValues);
    }

    public static AgentQuestSnapshot emptyLv1Beginner() {
        return new AgentQuestSnapshot(1, 0, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public AgentQuestStatus statusOf(int questId) {
        return questStatuses.getOrDefault(questId, AgentQuestStatus.NOT_STARTED);
    }

    public boolean hasCompleted(int questId) {
        return statusOf(questId) == AgentQuestStatus.COMPLETED;
    }

    public int itemCount(int itemId) {
        return inventoryCounts.getOrDefault(itemId, 0);
    }

    public int mobKillCount(int mobId) {
        return mobKillCounts.getOrDefault(mobId, 0);
    }

    public int questProgress(int progressId) {
        return questProgressValues.getOrDefault(progressId, 0);
    }

    public boolean jobAllowed(Set<Integer> allowedJobs) {
        return allowedJobs == null || allowedJobs.isEmpty() || allowedJobs.contains(jobId);
    }
}
