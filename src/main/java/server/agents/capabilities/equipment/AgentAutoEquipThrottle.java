package server.agents.capabilities.equipment;

import client.Character;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentAutoEquipThrottle {
    private static final long AUTO_EQUIP_THROTTLE_MS = 30_000L;
    private static final Map<Integer, Long> LAST_AUTO_EQUIP_MS = new ConcurrentHashMap<>();

    private AgentAutoEquipThrottle() {
    }

    public static boolean shouldRun(Character agent, long nowMs, boolean force) {
        if (force) {
            if (agent != null) {
                LAST_AUTO_EQUIP_MS.put(agent.getId(), nowMs);
            }
            return true;
        }
        if (agent == null) {
            return true;
        }
        int agentId = agent.getId();
        Long previous = LAST_AUTO_EQUIP_MS.get(agentId);
        if (previous != null && nowMs - previous < AUTO_EQUIP_THROTTLE_MS) {
            return false;
        }
        LAST_AUTO_EQUIP_MS.put(agentId, nowMs);
        return true;
    }

    static void clearForTest() {
        LAST_AUTO_EQUIP_MS.clear();
    }
}
