package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AmherstScopePolicy {
    private enum Profile {
        AMHERST,
        SOUTHPERRY
    }

    private static final Set<Integer> AMHERST_MAP_IDS = Set.of(
            10000, 20000, 30000, 30001, 40000, 50000, 1000000);
    private static final Set<Integer> SOUTHPERRY_MAP_IDS = Set.of(
            1000000, 1010000, 1010100, 1010200, 1010300, 1010400, 1020000, 2000000);
    private static final Set<Integer> LEGACY_EXCLUDED_QUEST_IDS = Set.of(
            1000, 1001, 1003, 1004, 1005, 1006, 1018, 1025, 1029, 1030, 8031);
    private static final Set<Integer> LATER_MAP_QUEST_IDS = Set.of(1007, 1016, 1017, 1019, 1022, 1026, 1027, 1028,
            1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046, 8020, 8021, 8022, 8023, 8024, 8025, 8142);
    private static final Map<Integer, Set<Integer>> AMHERST_ROUTE_EDGES = Map.of(
            10000, Set.of(20000),
            20000, Set.of(30000),
            30000, Set.of(30001, 40000),
            30001, Set.of(30000),
            40000, Set.of(50000),
            50000, Set.of(1000000),
            1000000, Set.of(50000));
    private static final Map<Integer, Set<Integer>> SOUTHPERRY_ROUTE_EDGES = Map.of(
            1000000, Set.of(1010000),
            1010000, Set.of(1000000, 1010100, 1010200, 1010300, 1010400, 1020000),
            1010100, Set.of(1010000),
            1010200, Set.of(1010000),
            1010300, Set.of(1010000),
            1010400, Set.of(1010000),
            1020000, Set.of(1010000, 2000000),
            2000000, Set.of(1020000));

    private final Profile profile;

    public AmherstScopePolicy() {
        this(Profile.AMHERST);
    }

    private AmherstScopePolicy(Profile profile) {
        this.profile = profile;
    }

    public static AmherstScopePolicy southperry() {
        return new AmherstScopePolicy(Profile.SOUTHPERRY);
    }

    public AmherstScopeDecision checkQuest(int questId) {
        if (profile == Profile.SOUTHPERRY) {
            if (MapleIslandSouthperryQuestCatalog.isRequiredQuest(questId)) {
                return AmherstScopeDecision.allow();
            }
            if (questId == MapleIslandSouthperryQuestCatalog.FORBIDDEN_SHANKS_QUEST_ID) {
                return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST,
                        "Shanks travel quest must remain incomplete in the Southperry MVP");
            }
            if (questId == MapleIslandSouthperryQuestCatalog.BIGGS_COLLECTION_QUEST_ID) {
                return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                        "Biggs's Collection is unavailable from the captured female starter baseline");
            }
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "quest is not part of the Southperry MVP catalog");
        }
        if (AmherstQuestCatalog.isRequiredQuest(questId)) {
            return AmherstScopeDecision.allow();
        }
        if (LEGACY_EXCLUDED_QUEST_IDS.contains(questId)) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST,
                    "legacy/tutorial-sensitive quest excluded from Amherst sub-phase");
        }
        if (LATER_MAP_QUEST_IDS.contains(questId)) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "quest belongs to a later Maple Island segment");
        }
        return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                "quest is not part of the Amherst sub-phase catalog");
    }

    public AmherstScopeDecision checkMap(int mapId) {
        if (allowedMapIds().contains(mapId)) {
            return AmherstScopeDecision.allow();
        }
        return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                profile == Profile.AMHERST
                        ? "map is outside the begin-to-Amherst sub-phase route"
                        : "map is outside the Amherst-to-Southperry MVP route");
    }

    public Integer nextHopMap(int sourceMapId, int destinationMapId) {
        Set<Integer> allowedMapIds = allowedMapIds();
        if (sourceMapId == destinationMapId || !allowedMapIds.contains(sourceMapId)
                || !allowedMapIds.contains(destinationMapId)) {
            return null;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<Integer, Integer> previous = new HashMap<>();
        queue.add(sourceMapId);
        previous.put(sourceMapId, sourceMapId);
        while (!queue.isEmpty() && !previous.containsKey(destinationMapId)) {
            int current = queue.removeFirst();
            for (Integer next : routeEdges().getOrDefault(current, Set.of())) {
                if (!previous.containsKey(next)) {
                    previous.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!previous.containsKey(destinationMapId)) {
            return null;
        }
        int step = destinationMapId;
        while (previous.get(step) != sourceMapId) {
            step = previous.get(step);
        }
        return step;
    }

    public AmherstScopeDecision checkNpcTravel(int npcId) {
        if (profile == Profile.AMHERST && npcId == AmherstQuestCatalog.SHANKS_NPC_ID) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                    "Shanks travel would leave Maple Island and is forbidden for this sub-phase");
        }
        return AmherstScopeDecision.allow();
    }

    public AmherstScopeDecision checkNpcTransport(int npcId) {
        if (npcId == AmherstQuestCatalog.SHANKS_NPC_ID) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                    "Shanks transport off Maple Island is forbidden");
        }
        return AmherstScopeDecision.allow();
    }

    public Integer scriptedPortalId(int sourceMapId, int destinationMapId) {
        if (profile == Profile.SOUTHPERRY && sourceMapId == 1010000
                && Set.of(1010100, 1010200, 1010300, 1010400).contains(destinationMapId)) {
            return 1;
        }
        return null;
    }

    private Set<Integer> allowedMapIds() {
        return profile == Profile.AMHERST ? AMHERST_MAP_IDS : SOUTHPERRY_MAP_IDS;
    }

    private Map<Integer, Set<Integer>> routeEdges() {
        return profile == Profile.AMHERST ? AMHERST_ROUTE_EDGES : SOUTHPERRY_ROUTE_EDGES;
    }
}
