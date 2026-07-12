package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AmherstScopePolicy {
    private static final Set<Integer> ALLOWED_MAP_IDS = Set.of(10000, 20000, 30000, 30001, 40000, 50000, 1000000);
    private static final Set<Integer> LEGACY_EXCLUDED_QUEST_IDS = Set.of(
            1000, 1001, 1003, 1004, 1005, 1006, 1018, 1025, 1029, 1030, 8031);
    private static final Set<Integer> LATER_MAP_QUEST_IDS = Set.of(1007, 1016, 1017, 1019, 1022, 1026, 1027, 1028,
            1039, 1040, 1041, 1042, 1043, 1044, 1046, 8020, 8021, 8022, 8023, 8024, 8025, 8142);
    private static final Map<Integer, Set<Integer>> ROUTE_EDGES = Map.of(
            10000, Set.of(20000),
            20000, Set.of(30000),
            30000, Set.of(30001, 40000),
            30001, Set.of(30000),
            40000, Set.of(50000),
            50000, Set.of(1000000),
            1000000, Set.of(50000));

    public AmherstScopeDecision checkQuest(int questId) {
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
        if (ALLOWED_MAP_IDS.contains(mapId)) {
            return AmherstScopeDecision.allow();
        }
        return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                "map is outside the begin-to-Amherst sub-phase route");
    }

    public Integer nextHopMap(int sourceMapId, int destinationMapId) {
        if (sourceMapId == destinationMapId || !ALLOWED_MAP_IDS.contains(sourceMapId)
                || !ALLOWED_MAP_IDS.contains(destinationMapId)) {
            return null;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<Integer, Integer> previous = new HashMap<>();
        queue.add(sourceMapId);
        previous.put(sourceMapId, sourceMapId);
        while (!queue.isEmpty() && !previous.containsKey(destinationMapId)) {
            int current = queue.removeFirst();
            for (Integer next : ROUTE_EDGES.getOrDefault(current, Set.of())) {
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
        if (npcId == AmherstQuestCatalog.SHANKS_NPC_ID) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                    "Shanks travel would leave Maple Island and is forbidden for this sub-phase");
        }
        return AmherstScopeDecision.allow();
    }
}
