package server.agents.capabilities.quest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** @deprecated Location catalogs live with the Amherst plan; retained as a source-compatibility facade. */
@Deprecated
public final class AmherstQuestCatalog {
    public static final int START_MAP_ID = server.agents.plans.amherst.AmherstQuestCatalog.START_MAP_ID;
    public static final int FINAL_MAP_ID = server.agents.plans.amherst.AmherstQuestCatalog.FINAL_MAP_ID;
    public static final int SHANKS_NPC_ID = server.agents.plans.amherst.AmherstQuestCatalog.SHANKS_NPC_ID;
    private AmherstQuestCatalog() {}
    public static List<AmherstQuestDefinition> allRequiredQuests() { return server.agents.plans.amherst.AmherstQuestCatalog.allRequiredQuests(); }
    public static List<Integer> requiredQuestIds() { return server.agents.plans.amherst.AmherstQuestCatalog.requiredQuestIds(); }
    public static Optional<AmherstQuestDefinition> find(int questId) { return server.agents.plans.amherst.AmherstQuestCatalog.find(questId); }
    public static boolean isRequiredQuest(int questId) { return server.agents.plans.amherst.AmherstQuestCatalog.isRequiredQuest(questId); }
    public static Set<Integer> requiredQuestIdSet() { return server.agents.plans.amherst.AmherstQuestCatalog.requiredQuestIdSet(); }
    public static Optional<String> npcName(int npcId) { return server.agents.plans.amherst.AmherstQuestCatalog.npcName(npcId); }
    public static List<AmherstQuestDefinition> routeOrder() { return server.agents.plans.amherst.AmherstQuestCatalog.routeOrder(); }
}
