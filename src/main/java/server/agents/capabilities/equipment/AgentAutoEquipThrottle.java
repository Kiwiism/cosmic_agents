package server.agents.capabilities.equipment;

import client.Character;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentAutoEquipThrottle {
    private static final long AUTO_EQUIP_THROTTLE_MS = config.AgentTuning.longValue("server.agents.capabilities.equipment.AgentAutoEquipThrottle.AUTO_EQUIP_THROTTLE_MS");
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

    public static void clearAgentRuntimeState(int agentId) {
        LAST_AUTO_EQUIP_MS.remove(agentId);
    }

    static void clearForTest() {
        LAST_AUTO_EQUIP_MS.clear();
    }
}
