package server.agents.capabilities.quest;

import java.util.Map;
import java.util.Set;

public record AgentQuestRequirement(
        int questId,
        String questName,
        int startNpcId,
        int completeNpcId,
        int minLevel,
        int maxLevel,
        Set<Integer> allowedJobIds,
        Set<Integer> prerequisiteQuestIds,
        Map<Integer, Integer> requiredItems,
        Map<Integer, Integer> requiredMobKills,
        Map<Integer, Integer> requiredProgressValues,
        boolean autoComplete) {

    public AgentQuestRequirement {
        allowedJobIds = allowedJobIds == null ? Set.of() : Set.copyOf(allowedJobIds);
        prerequisiteQuestIds = prerequisiteQuestIds == null ? Set.of() : Set.copyOf(prerequisiteQuestIds);
        requiredItems = requiredItems == null ? Map.of() : Map.copyOf(requiredItems);
        requiredMobKills = requiredMobKills == null ? Map.of() : Map.copyOf(requiredMobKills);
        requiredProgressValues = requiredProgressValues == null ? Map.of() : Map.copyOf(requiredProgressValues);
    }

    public static AgentQuestRequirement fromAmherst(AmherstQuestDefinition definition) {
        return new AgentQuestRequirement(
                definition.questId(),
                definition.questName(),
                definition.startNpc().id(),
                definition.completeNpc().id(),
                1,
                0,
                Set.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                definition.completionType() == AmherstQuestCompletionType.AUTO_COMPLETE);
    }
}
