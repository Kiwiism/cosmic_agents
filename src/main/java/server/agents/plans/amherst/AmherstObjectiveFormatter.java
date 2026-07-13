package server.agents.plans.amherst;

import server.agents.capabilities.quest.MapleIslandSouthperryQuestCatalog;

import java.util.List;

public final class AmherstObjectiveFormatter {
    private AmherstObjectiveFormatter() {
    }

    public static String numbered(AmherstPlanCard card, AmherstPlanObjective objective) {
        int index = card.objectives().indexOf(objective) + 1;
        return index + "/" + card.objectives().size() + " " + describe(objective);
    }

    public static String describe(AmherstPlanObjective objective) {
        return switch (objective.kind()) {
            case QUEST_START -> "Start " + quest(objective.questId()) + npc(objective.npcId());
            case QUEST_COMPLETE -> "Complete " + quest(objective.questId()) + npc(objective.npcId());
            case QUEST_CHAIN -> "Quest chain " + quests(objective.questIds());
            case QUEST_CHAIN_IF_AVAILABLE -> "Available quest chain " + quests(objective.questIds());
            case USE_ITEM -> "Use item " + objective.itemId() + " for " + quest(objective.questId());
            case KILL_MOBS -> "Hunt " + killRequirements(objective) + " for " + quest(objective.questId());
            case REACTOR_HIT -> "Hit Amherst reactor for " + quest(objective.questId());
            case REACTOR_BOX_ITEMS -> "Collect reactor items " + objective.itemIds()
                    + " for " + quest(objective.questId());
            case STOP_PLAN -> "Stop plan: " + objective.reason();
        } + " [map " + objective.mapId() + "]";
    }

    public static String expectedSteps(AmherstPlanObjective objective) {
        return switch (objective.kind()) {
            case QUEST_START, QUEST_COMPLETE, QUEST_CHAIN, QUEST_CHAIN_IF_AVAILABLE ->
                    "navigate, talk to NPC, apply normal quest transition, verify quest state";
            case USE_ITEM -> "inspect inventory, use item normally, verify inventory and quest state";
            case KILL_MOBS -> objective.itemIds().isEmpty()
                    ? "verify quest, navigate, fight required mobs, verify kill progress"
                    : "verify quest, navigate, fight required mobs, loot required items, verify progress";
            case REACTOR_HIT, REACTOR_BOX_ITEMS ->
                    "verify quest, approach reactor, hit through its cooldown states, loot normally, verify inventory";
            case STOP_PLAN -> "relaxer".equalsIgnoreCase(objective.mode())
                    ? "reach final map, verify required final state, sit on Relaxer"
                    : "reach final map and verify required final state";
        };
    }

    private static String quest(int questId) {
        return MapleIslandSouthperryQuestCatalog.findAny(questId)
                .map(definition -> "quest " + questId + " - " + definition.questName())
                .orElse("quest " + questId);
    }

    private static String quests(List<Integer> questIds) {
        return questIds.toString();
    }

    private static String npc(Integer npcId) {
        return npcId == null ? "" : " at NPC " + npcId;
    }

    private static String killRequirements(AmherstPlanObjective objective) {
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < objective.mobIds().size(); i++) {
            if (i > 0) {
                description.append(", ");
            }
            description.append(objective.counts().get(i)).append(" x mob ").append(objective.mobIds().get(i));
        }
        return description.toString();
    }
}
