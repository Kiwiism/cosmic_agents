package server.agents.plans.amherst;

import java.util.List;

public record AmherstPlanObjective(
        String objectiveId,
        AmherstPlanObjectiveKind kind,
        int routeIndex,
        int objectiveIndex,
        int mapId,
        Integer questId,
        List<Integer> questIds,
        Integer npcId,
        List<Integer> npcIds,
        Integer itemId,
        List<Integer> itemIds,
        List<Integer> mobIds,
        List<Integer> counts,
        String mode,
        String reason) {

    public AmherstPlanObjective {
        questIds = questIds == null ? List.of() : List.copyOf(questIds);
        npcIds = npcIds == null ? List.of() : List.copyOf(npcIds);
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        mobIds = mobIds == null ? List.of() : List.copyOf(mobIds);
        counts = counts == null ? List.of() : List.copyOf(counts);
    }

    public List<Integer> allQuestIds() {
        if (questId != null) {
            return List.of(questId);
        }
        return questIds;
    }
}
