package server.agents.plans.amherst;

import client.Character;
import config.YamlConfig;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.integration.AgentPacketGatewayRuntime;

import java.util.Map;

public final class AmherstPlanNarrator {
    private static final Map<Integer, String> MAP_NAMES = Map.ofEntries(
            Map.entry(10000, "Mushroom Town"),
            Map.entry(20000, "Snail Garden"),
            Map.entry(30000, "Snail Field of Flowers"),
            Map.entry(30001, "Mushroom Town Townstreet"),
            Map.entry(40000, "Mushroom Town Training Ground"),
            Map.entry(50000, "Dangerous Forest"),
            Map.entry(1000000, "Amherst"),
            Map.entry(1010000, "Entrance to Adventurer Training Center"),
            Map.entry(1010100, "Mai's First Training Ground"),
            Map.entry(1010200, "Mai's Second Training Ground"),
            Map.entry(1010300, "Mai's Third Training Ground"),
            Map.entry(1010400, "Mai's Final Training Ground"),
            Map.entry(1020000, "Split Road of Destiny"),
            Map.entry(2000000, "Southperry"));
    private static final Map<Integer, String> MOB_NAMES = Map.ofEntries(
            Map.entry(100100, "Snail"),
            Map.entry(100101, "Blue Snail"),
            Map.entry(120100, "Shroom"),
            Map.entry(1210102, "Orange Mushroom"),
            Map.entry(130100, "Stump"),
            Map.entry(130101, "Red Snail"),
            Map.entry(210100, "Slime"),
            Map.entry(9500102, "Training Orange Mushroom"),
            Map.entry(9300018, "Tutorial Jr. Sentinel"));
    private static final Map<Integer, String> ITEM_NAMES = Map.ofEntries(
            Map.entry(4000001, "Orange Mushroom Cap"),
            Map.entry(4000003, "Tree Branch"),
            Map.entry(4000004, "Squishy Liquid"),
            Map.entry(2010007, "Roger's Apple"),
            Map.entry(4031802, "Jr. Sentinel Shellpiece"),
            Map.entry(4031161, "Old Wooden Board"),
            Map.entry(4031162, "Old Screw"));

    private AmherstPlanNarrator() {
    }

    public static void announce(Character agent, AmherstPlanObjective objective) {
        if (agent == null || objective == null
                || !config.AgentYamlConfig.config.agent.AGENT_AMHERST_INTENTION_CHAT_ENABLED) {
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
            case FORCE_COMPLETE_QUEST -> objective.questId() == 8020
                    ? "I'm going to talk to Yoona, visit the Cash Shop for her shopping guide, then come back."
                    : objective.questId() >= 8021 && objective.questId() <= 8025
                    ? "I'm going to talk to Yoona and answer " + quest(objective.questId()) + "."
                    : "I'm going to " + map + " to talk to " + npc(objective.npcId())
                    + " and complete " + quest(objective.questId()) + ".";
            case QUEST_CHAIN, QUEST_CHAIN_IF_AVAILABLE -> isSingleRainQuiz(objective)
                    ? "I'm going to " + map + " to talk to Rain and answer "
                    + quest(objective.questIds().getFirst()) + "."
                    : "I'm going to " + map + " to talk to " + chainNpc(objective)
                    + " and work through " + questChain(objective) + ".";
            case USE_ITEM -> "I'm using " + item(objective.itemId()) + " for "
                    + quest(objective.questId()) + ".";
            case KILL_MOBS -> "I'm going to " + map + " to hunt " + mobs(objective)
                    + lootSuffix(objective) + " for " + quest(objective.questId()) + ".";
            case REACTOR_HIT -> "I'm going to " + map
                    + " to break Pio's recycling boxes for " + quest(objective.questId()) + ".";
            case REACTOR_BOX_ITEMS -> "I'm collecting the recycled goods for "
                    + quest(objective.questId()) + ".";
            case STOP_PLAN -> objective.mapId() == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                    ? "I've reached Southperry. I'm going to find an open spot and rest on my Relaxer."
                    : "relaxer".equalsIgnoreCase(objective.mode())
                    ? "I've reached Amherst. I'm going to find an open spot and rest on my Relaxer."
                    : "I've reached Amherst, so I'm stopping this quest run here.";
        };
    }

    private static String quest(int questId) {
        return MapleIslandSouthperryQuestCatalog.findAny(questId)
                .map(definition -> definition.questName())
                .orElse("quest " + questId);
    }

    private static String npc(Integer npcId) {
        if (npcId == null) {
            return "the quest NPC";
        }
        return MapleIslandSouthperryQuestCatalog.npcName(npcId)
                .or(() -> AmherstQuestCatalog.npcName(npcId))
                .orElse("NPC " + npcId);
    }

    private static String questChain(AmherstPlanObjective objective) {
        if (objective.questIds().size() == 1) {
            return quest(objective.questIds().getFirst());
        }
        return quest(objective.questIds().getFirst()) + " quest chain";
    }

    private static boolean isSingleRainQuiz(AmherstPlanObjective objective) {
        return objective.questIds().size() == 1
                && objective.questIds().getFirst() >= 1009
                && objective.questIds().getFirst() <= 1015;
    }

    private static String chainNpc(AmherstPlanObjective objective) {
        if (objective.npcId() != null) {
            return npc(objective.npcId());
        }
        if (!objective.questIds().isEmpty()) {
            return MapleIslandSouthperryQuestCatalog.findAny(objective.questIds().getFirst())
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
