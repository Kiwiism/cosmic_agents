package server.agents.plans.amherst;

import client.Character;
import config.YamlConfig;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.integration.AgentPacketGatewayRuntime;

import java.util.Map;

public final class AmherstPlanNarrator {
    private static final Map<Integer, String> MAP_NAMES = Map.of(
            10000, "Mushroom Town",
            20000, "Snail Garden",
            30000, "Snail Field of Flowers",
            30001, "Mushroom Town Townstreet",
            40000, "Mushroom Town Training Ground",
            50000, "Dangerous Forest",
            1000000, "Amherst");
    private static final Map<Integer, String> MOB_NAMES = Map.of(
            100100, "Snail",
            100101, "Blue Snail",
            130101, "Red Snail",
            9300018, "Tutorial Jr. Sentinel");
    private static final Map<Integer, String> ITEM_NAMES = Map.of(
            2010007, "Roger's Apple",
            4031802, "Jr. Sentinel Shellpiece",
            4031161, "Old Wooden Board",
            4031162, "Old Screw");

    private AmherstPlanNarrator() {
    }

    public static void announce(Character agent, AmherstPlanObjective objective) {
        if (agent == null || objective == null
                || !YamlConfig.config.server.AGENT_AMHERST_INTENTION_CHAT_ENABLED) {
            return;
        }
        AgentPacketGatewayRuntime.packets().broadcastChatText(
                agent, AgentChatTextSanitizer.sanitize(message(objective)), false, 0);
    }

    static String message(AmherstPlanObjective objective) {
        String map = MAP_NAMES.getOrDefault(objective.mapId(), "map " + objective.mapId());
        return switch (objective.kind()) {
            case QUEST_START -> "I'm going to " + map + " to talk to " + npc(objective.npcId())
                    + " and start " + quest(objective.questId()) + ".";
            case QUEST_COMPLETE -> "I'm going to " + map + " to talk to " + npc(objective.npcId())
                    + " and complete " + quest(objective.questId()) + ".";
            case QUEST_CHAIN, QUEST_CHAIN_IF_AVAILABLE -> "I'm going to " + map
                    + " to talk to " + chainNpc(objective) + " and work through "
                    + questChain(objective) + ".";
            case USE_ITEM -> "I'm using " + item(objective.itemId()) + " for "
                    + quest(objective.questId()) + ".";
            case KILL_MOBS -> "I'm going to " + map + " to hunt " + mobs(objective)
                    + lootSuffix(objective) + " for " + quest(objective.questId()) + ".";
            case REACTOR_HIT -> "I'm going to " + map
                    + " to break Pio's recycling boxes for " + quest(objective.questId()) + ".";
            case REACTOR_BOX_ITEMS -> "I'm collecting the recycled goods for "
                    + quest(objective.questId()) + ".";
            case STOP_PLAN -> "relaxer".equalsIgnoreCase(objective.mode())
                    ? "I've reached Amherst. I'm going to sit on the Relaxer and rest."
                    : "I've reached Amherst, so I'm stopping this quest run here.";
        };
    }

    private static String quest(int questId) {
        return AmherstQuestCatalog.find(questId)
                .map(definition -> definition.questName())
                .orElse("quest " + questId);
    }

    private static String npc(Integer npcId) {
        if (npcId == null) {
            return "the quest NPC";
        }
        return AmherstQuestCatalog.npcName(npcId).orElse("NPC " + npcId);
    }

    private static String questChain(AmherstPlanObjective objective) {
        if (objective.questIds().size() == 1) {
            return quest(objective.questIds().getFirst());
        }
        return quest(objective.questIds().getFirst()) + " quest chain";
    }

    private static String chainNpc(AmherstPlanObjective objective) {
        if (objective.npcId() != null) {
            return npc(objective.npcId());
        }
        if (!objective.questIds().isEmpty()) {
            return AmherstQuestCatalog.find(objective.questIds().getFirst())
                    .map(definition -> npc(definition.startNpc().id()))
                    .orElse("the quest NPC");
        }
        return "the quest NPC";
    }

    private static String mobs(AmherstPlanObjective objective) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < objective.mobIds().size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            int mobId = objective.mobIds().get(index);
            text.append(objective.counts().get(index)).append(' ')
                    .append(MOB_NAMES.getOrDefault(mobId, "mob " + mobId));
        }
        return text.toString();
    }

    private static String lootSuffix(AmherstPlanObjective objective) {
        if (objective.itemIds().isEmpty()) {
            return "";
        }
        return " and collect " + item(objective.itemIds().getFirst());
    }

    private static String item(Integer itemId) {
        if (itemId == null) {
            return "the required item";
        }
        return ITEM_NAMES.getOrDefault(itemId, "item " + itemId);
    }
}
