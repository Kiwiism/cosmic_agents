package server.agents.capabilities.quest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** @deprecated Location catalogs live with the Amherst plan; retained as a source-compatibility facade. */
@Deprecated
public final class MapleIslandSouthperryQuestCatalog {
    public static final int START_MAP_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.START_MAP_ID;
    public static final int TRAINING_CENTER_MAP_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.TRAINING_CENTER_MAP_ID;
    public static final int SPLIT_ROAD_MAP_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.SPLIT_ROAD_MAP_ID;
    public static final int FINAL_MAP_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID;
    public static final int SHANKS_NPC_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID;
    public static final int BIGGS_COLLECTION_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.BIGGS_COLLECTION_QUEST_ID;
    public static final int FORBIDDEN_SHANKS_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.FORBIDDEN_SHANKS_QUEST_ID;
    public static final int START_ONLY_BIGGS_STORY_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID;
    public static final int YOONA_SHOPPING_GUIDE_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.YOONA_SHOPPING_GUIDE_QUEST_ID;
    public static final int YOONA_QUIZ_1_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.YOONA_QUIZ_1_QUEST_ID;
    public static final int YOONA_QUIZ_2_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.YOONA_QUIZ_2_QUEST_ID;
    public static final int YOONA_QUIZ_3_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.YOONA_QUIZ_3_QUEST_ID;
    public static final int YOONA_QUIZ_4_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.YOONA_QUIZ_4_QUEST_ID;
    public static final int YOONA_QUIZ_5_QUEST_ID = server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.YOONA_QUIZ_5_QUEST_ID;
    private MapleIslandSouthperryQuestCatalog() {}
    public static List<AmherstQuestDefinition> allRequiredQuests() { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.allRequiredQuests(); }
    public static Optional<AmherstQuestDefinition> find(int id) { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.find(id); }
    public static Optional<AmherstQuestDefinition> findAny(int id) { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.findAny(id); }
    public static boolean isRequiredQuest(int id) { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.isRequiredQuest(id); }
    public static Set<Integer> requiredQuestIdSet() { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.requiredQuestIdSet(); }
    public static Set<Integer> completedQuestIdSet() { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.completedQuestIdSet(); }
    public static Optional<String> npcName(int id) { return server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog.npcName(id); }
}
