package server.agents.observer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Route and timing policy for the independent Maple Island observer showcase. */
final class AgentObserverPolicy {
    private static final String TUNING_PREFIX =
            "server.agents.observer.AgentObserverPolicy.";
    static final int MUSHROOM_TOWN_MAP_ID = 10_000;
    static final int STATION_MAP_ID = 20_000;
    static final int GREEN_SNAIL_MAP_ID = 50_000;
    static final int AMHERST_MAP_ID = 1_000_000;
    static final int MAI_MAP_ID = 1_010_000;
    static final int SOUTHPERRY_MAP_ID = 2_000_000;
    static final int LITH_HARBOR_MAP_ID = 104_000_000;
    static final int SHANKS_NPC_ID = 22_000;
    static final int BARI_TEST_QUEST_ID = 1_045;

    static final List<Integer> APPROACH_ROUTE =
            List.of(STATION_MAP_ID, 30_000, 30_001, 40_000, GREEN_SNAIL_MAP_ID);
    static final List<Integer> ROAM_CYCLE =
            List.of(GREEN_SNAIL_MAP_ID, AMHERST_MAP_ID, MAI_MAP_ID, AMHERST_MAP_ID);
    static final List<Integer> ISOLATED_TRAINING_MAPS =
            List.of(1_010_100, 1_010_200, 1_010_300, 1_010_400);
    static final Set<Integer> ISOLATED_TRAINING_MAP_SET = Set.copyOf(ISOLATED_TRAINING_MAPS);

    private static final int IDLE_MIN_MS = tuningInt("IDLE_MIN_MS");
    private static final int IDLE_MAX_MS = tuningInt("IDLE_MAX_MS");
    private static final int EXCURSION_MIN_MS = tuningInt("EXCURSION_MIN_MS");
    private static final int EXCURSION_MAX_MS = tuningInt("EXCURSION_MAX_MS");
    private static final int EXCURSION_CHANCE_PERCENT = tuningInt("EXCURSION_CHANCE_PERCENT");
    private static final int INVESTIGATION_HOLD_MS = tuningInt("INVESTIGATION_HOLD_MS");
    private static final int INVESTIGATION_TIMEOUT_MS = tuningInt("INVESTIGATION_TIMEOUT_MS");
    private static final int LITH_IDLE_MIN_MS = tuningInt("LITH_IDLE_MIN_MS");
    private static final int LITH_IDLE_MAX_MS = tuningInt("LITH_IDLE_MAX_MS");
    private static final int F1_COOLDOWN_MS = tuningInt("F1_COOLDOWN_MS");

    private static final Map<Integer, List<Integer>> ROUTES = routes();

    private AgentObserverPolicy() {
    }

    static int idleDelayMs() {
        return between(IDLE_MIN_MS, IDLE_MAX_MS);
    }

    static int excursionDelayMs() {
        return between(EXCURSION_MIN_MS, EXCURSION_MAX_MS);
    }

    static int lithIdleDelayMs() {
        return between(LITH_IDLE_MIN_MS, LITH_IDLE_MAX_MS);
    }

    static int investigationHoldMs() {
        return INVESTIGATION_HOLD_MS;
    }

    static int investigationTimeoutMs() {
        return INVESTIGATION_TIMEOUT_MS;
    }

    static int f1CooldownMs() {
        return F1_COOLDOWN_MS;
    }

    static boolean shouldExcursion() {
        return ThreadLocalRandom.current().nextInt(100) < EXCURSION_CHANCE_PERCENT;
    }

    static int randomTrainingMap() {
        return ISOLATED_TRAINING_MAPS.get(
                ThreadLocalRandom.current().nextInt(ISOLATED_TRAINING_MAPS.size()));
    }

    static boolean watchedReachedRoamingRoute(int mapId) {
        return APPROACH_ROUTE.contains(mapId)
                || ROAM_CYCLE.contains(mapId)
                || mapId == 1_020_000
                || mapId == SOUTHPERRY_MAP_ID
                || ISOLATED_TRAINING_MAP_SET.contains(mapId);
    }

    static int approachIndex(int mapId) {
        return APPROACH_ROUTE.indexOf(mapId);
    }

    static int cycleIndex(int mapId) {
        return ROAM_CYCLE.indexOf(mapId);
    }

    static Integer nextHop(int sourceMapId, int destinationMapId) {
        if (sourceMapId == destinationMapId) {
            return sourceMapId;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<Integer, Integer> previous = new HashMap<>();
        queue.add(sourceMapId);
        previous.put(sourceMapId, sourceMapId);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next : ROUTES.getOrDefault(current, List.of())) {
                if (previous.putIfAbsent(next, current) != null) {
                    continue;
                }
                if (next == destinationMapId) {
                    int hop = next;
                    while (previous.get(hop) != sourceMapId) {
                        hop = previous.get(hop);
                    }
                    return hop;
                }
                queue.addLast(next);
            }
        }
        return null;
    }

    private static Map<Integer, List<Integer>> routes() {
        Map<Integer, List<Integer>> mutable = new LinkedHashMap<>();
        connect(mutable, MUSHROOM_TOWN_MAP_ID, STATION_MAP_ID);
        connect(mutable, STATION_MAP_ID, 30_000);
        connect(mutable, 30_000, 30_001);
        connect(mutable, 30_000, 40_000);
        connect(mutable, 40_000, GREEN_SNAIL_MAP_ID);
        connect(mutable, GREEN_SNAIL_MAP_ID, AMHERST_MAP_ID);
        connect(mutable, AMHERST_MAP_ID, MAI_MAP_ID);
        connect(mutable, MAI_MAP_ID, 1_020_000);
        connect(mutable, 1_020_000, SOUTHPERRY_MAP_ID);
        Map<Integer, List<Integer>> immutable = new LinkedHashMap<>();
        mutable.forEach((mapId, neighbors) -> immutable.put(mapId, List.copyOf(neighbors)));
        return Map.copyOf(immutable);
    }

    private static void connect(Map<Integer, List<Integer>> routes, int left, int right) {
        routes.computeIfAbsent(left, ignored -> new ArrayList<>()).add(right);
        routes.computeIfAbsent(right, ignored -> new ArrayList<>()).add(left);
    }

    private static int between(int min, int max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static int tuningInt(String key) {
        return config.AgentTuning.intValue(TUNING_PREFIX + key);
    }
}
