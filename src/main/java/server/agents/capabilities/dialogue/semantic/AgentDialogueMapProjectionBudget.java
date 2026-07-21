package server.agents.capabilities.dialogue.semantic;

import client.Character;
import config.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small bounded per-map spacing gate for semantic chat projection. */
public final class AgentDialogueMapProjectionBudget {
    private static final int MAX_TRACKED_MAPS = 4_096;
    private static final Map<MapKey, Long> nextAllowedAt = new LinkedHashMap<>();

    private AgentDialogueMapProjectionBudget() {
    }

    public static synchronized boolean tryAcquire(Character speaker, long nowMs) {
        if (speaker == null || speaker.getMap() == null) {
            return false;
        }
        MapKey key = new MapKey(speaker.getWorld(), channel(speaker), speaker.getMapId());
        if (nowMs < nextAllowedAt.getOrDefault(key, 0L)) {
            return false;
        }
        if (nextAllowedAt.size() >= MAX_TRACKED_MAPS && !nextAllowedAt.containsKey(key)) {
            MapKey oldest = nextAllowedAt.keySet().iterator().next();
            nextAllowedAt.remove(oldest);
        }
        nextAllowedAt.put(key, saturatedAdd(nowMs,
                Math.max(0, YamlConfig.config.server.AGENT_DIALOGUE_MAP_MESSAGE_INTERVAL_MS)));
        return true;
    }

    static synchronized void resetForTests() {
        nextAllowedAt.clear();
    }

    private static int channel(Character speaker) {
        return speaker.getClient() == null ? 0 : speaker.getClient().getChannel();
    }

    private static long saturatedAdd(long value, long increment) {
        return Long.MAX_VALUE - value < increment ? Long.MAX_VALUE : value + increment;
    }

    private record MapKey(int world, int channel, int mapId) {
    }
}
