package server.agents.capabilities.quest;

import java.util.List;
import java.util.Map;

record AgentQuestRewardChoiceCatalog(
        int schemaVersion,
        String catalogId,
        List<String> priorityOrder,
        Map<Integer, Integer> fixedRewardItemByQuestId) {

    AgentQuestRewardChoiceCatalog {
        if (schemaVersion <= 0 || catalogId == null || catalogId.isBlank()
                || priorityOrder == null || priorityOrder.isEmpty()
                || fixedRewardItemByQuestId == null) {
            throw new IllegalArgumentException("a complete quest reward policy is required");
        }
        priorityOrder = List.copyOf(priorityOrder);
        fixedRewardItemByQuestId = Map.copyOf(fixedRewardItemByQuestId);
    }
}
