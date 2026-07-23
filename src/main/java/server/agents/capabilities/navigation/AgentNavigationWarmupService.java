package server.agents.capabilities.navigation;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent-owned map navigation warmup notification throttle.
 */
public final class AgentNavigationWarmupService {
    private static final long WARMUP_RETRY_SUPPRESSION_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.navigation.AgentNavigationWarmupService.WARMUP_RETRY_SUPPRESSION_MS");
    private static final Map<Integer, Map<Integer, Long>> WARMUP_NOTIFIED = new ConcurrentHashMap<>();

    private AgentNavigationWarmupService() {
    }

    public static void clearLeaderRuntimeState(int leaderId) {
        WARMUP_NOTIFIED.remove(leaderId);
    }

    public static void notifyWarmup(AgentRuntimeEntry entry, Character agent) {
        Character leader = AgentRelationshipRuntime.interactionTarget(entry);
        if (leader == null) {
            return;
        }
        int leaderId = leader.getId();
        int mapId = agent.getMap().getId();
        long now = System.currentTimeMillis();
        Map<Integer, Long> byMap = WARMUP_NOTIFIED.get(leaderId);
        if (byMap != null) {
            Long last = byMap.get(mapId);
            if (last != null && (now - last) < WARMUP_RETRY_SUPPRESSION_MS) {
                return;
            }
        }

        long walkable = agent.getMap().getFootholds().getAllFootholds().stream()
                .filter(fh -> !fh.isWall())
                .count();
        if (walkable < 100) {
            return;
        }
        WARMUP_NOTIFIED.computeIfAbsent(leaderId, k -> new ConcurrentHashMap<>()).put(mapId, now);
        leader.dropMessage(5, agent.getName() + " is warming map navigation cache, using fallback movement...");
    }
}
