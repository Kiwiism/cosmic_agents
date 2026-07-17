package server.agents.plans.amherst;

import server.agents.capabilities.quest.AmherstNpcRef;
import server.agents.capabilities.quest.AmherstQuestDefinition;
import server.agents.capabilities.quest.AmherstQuestFlag;
import server.agents.capabilities.quest.AmherstQuestPattern;
import server.agents.capabilities.quest.AmherstQuestSegment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MapleIslandSouthperryQuestCatalog {
    public static final int START_MAP_ID = 1000000;
    public static final int TRAINING_CENTER_MAP_ID = 1010000;
    public static final int SPLIT_ROAD_MAP_ID = 1020000;
    public static final int FINAL_MAP_ID = 2000000;
    public static final int SHANKS_NPC_ID = 22000;
    public static final int BIGGS_COLLECTION_QUEST_ID = 1007;
    public static final int FORBIDDEN_SHANKS_QUEST_ID = 1028;
    public static final int START_ONLY_BIGGS_STORY_QUEST_ID = 1046;
    public static final int YOONA_SHOPPING_GUIDE_QUEST_ID = 8020;
    public static final int YOONA_QUIZ_1_QUEST_ID = 8021;
    public static final int YOONA_QUIZ_2_QUEST_ID = 8022;
    public static final int YOONA_QUIZ_3_QUEST_ID = 8023;
    public static final int YOONA_QUIZ_4_QUEST_ID = 8024;
    public static final int YOONA_QUIZ_5_QUEST_ID = 8025;

    private static final AmherstNpcRef LUCAS = new AmherstNpcRef(12000, "Lucas");
    private static final AmherstNpcRef MAI = new AmherstNpcRef(12100, "Mai");
    private static final AmherstNpcRef YOONA = new AmherstNpcRef(20100, "Yoona");
    private static final AmherstNpcRef BARI = new AmherstNpcRef(20001, "Bari");
    private static final AmherstNpcRef BIGGS = new AmherstNpcRef(20002, "Biggs");

    private static final List<AmherstQuestDefinition> QUESTS = List.of(
            AmherstQuestDefinition.npc(1039, "Helping Out Yoona", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE),
            AmherstQuestDefinition.npc(YOONA_SHOPPING_GUIDE_QUEST_ID,
                    "Yoona's Quiz on Shopping : Start", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(YOONA_QUIZ_1_QUEST_ID,
                    "Yoona's Quiz on Shopping 1", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(YOONA_QUIZ_2_QUEST_ID,
                    "Yoona's Quiz on Shopping 2", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(YOONA_QUIZ_3_QUEST_ID,
                    "Yoona's Quiz on Shopping 3", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(YOONA_QUIZ_4_QUEST_ID,
                    "Yoona's Quiz on Shopping 4", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(YOONA_QUIZ_5_QUEST_ID,
                    "Yoona's Quiz on Shopping 5", YOONA, YOONA,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(1040, "Chief's Introduction", LUCAS, LUCAS,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(1041, "Mai's First Training", MAI, MAI,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE, AmherstQuestFlag.ITEM_TURN_IN),
            AmherstQuestDefinition.npc(1042, "Mai's Second Training", MAI, MAI,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE),
            AmherstQuestDefinition.npc(1043, "Mai's Third Training", MAI, MAI,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE, AmherstQuestFlag.ITEM_TURN_IN),
            AmherstQuestDefinition.npc(1044, "Mai's Last Training", MAI, MAI,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE),
            AmherstQuestDefinition.npc(1045, "Bari's Test", BARI, BARI,
                    AmherstQuestSegment.TRAINING_CENTER, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE, AmherstQuestFlag.ITEM_TURN_IN),
            AmherstQuestDefinition.npc(1046, "Biggs's Story on Victoria Island.", BIGGS, BIGGS,
                    AmherstQuestSegment.SOUTHPERRY, AmherstQuestPattern.NPC_DELIVERY));

    private static final Map<Integer, AmherstQuestDefinition> QUESTS_BY_ID = QUESTS.stream()
            .collect(Collectors.toUnmodifiableMap(AmherstQuestDefinition::questId, Function.identity()));

    private MapleIslandSouthperryQuestCatalog() {
    }

    public static List<AmherstQuestDefinition> allRequiredQuests() {
        return QUESTS;
    }

    public static Optional<AmherstQuestDefinition> find(int questId) {
        return Optional.ofNullable(QUESTS_BY_ID.get(questId));
    }

    public static boolean isRequiredQuest(int questId) {
        return QUESTS_BY_ID.containsKey(questId);
    }

    public static Set<Integer> requiredQuestIdSet() {
        return QUESTS_BY_ID.keySet();
    }

    public static Set<Integer> completedQuestIdSet() {
        return Set.of(1039, 1040, 1041, 1042, 1043, 1044, 1045,
                YOONA_SHOPPING_GUIDE_QUEST_ID, YOONA_QUIZ_1_QUEST_ID,
                YOONA_QUIZ_2_QUEST_ID, YOONA_QUIZ_3_QUEST_ID,
                YOONA_QUIZ_4_QUEST_ID, YOONA_QUIZ_5_QUEST_ID);
    }

    public static Optional<String> npcName(int npcId) {
        return QUESTS.stream()
                .flatMap(definition -> java.util.stream.Stream.of(
                        definition.startNpc(), definition.completeNpc()))
                .filter(npc -> npc.id() == npcId)
                .map(AmherstNpcRef::name)
                .findFirst();
    }

    public static Optional<AmherstQuestDefinition> findAny(int questId) {
        return find(questId).or(() -> AmherstQuestCatalog.find(questId));
    }
}
