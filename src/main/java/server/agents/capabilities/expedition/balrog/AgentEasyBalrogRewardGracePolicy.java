package server.agents.capabilities.expedition.balrog;

import client.Character;
import scripting.event.EventInstanceManager;
import server.maps.MapItem;

/** Central Agent-only reward-room grace period, measured from the reactor item spray. */
public final class AgentEasyBalrogRewardGracePolicy {
    public static final long HUMAN_LOOT_GRACE_MS = 7_000L;
    public static final int AGENT_PICKUP_DISTANCE_PX = 32;
    public static final String REWARD_OPENED_AT_PROPERTY = "balrogRewardOpenedAt";

    private AgentEasyBalrogRewardGracePolicy() {
    }

    public static boolean blocksAgentLoot(Character agent, long nowMs) {
        if (agent == null || agent.getMapId() != AgentBalrogDefinition.CLEAR_MAP) {
            return false;
        }
        EventInstanceManager event = agent.getEventInstance();
        if (event == null) {
            return false;
        }
        long openedAtMs = rewardOpenedAt(event);
        return openedAtMs == 0L || nowMs < openedAtMs + HUMAN_LOOT_GRACE_MS;
    }

    public static boolean permitsAgentLoot(Character agent, MapItem drop, long nowMs) {
        if (agent == null || agent.getMapId() != AgentBalrogDefinition.CLEAR_MAP
                || agent.getEventInstance() == null) {
            return true;
        }
        return !blocksAgentLoot(agent, nowMs)
                && AgentEasyBalrogRewardClaimRegistry.isAssignedTo(agent, drop)
                && agent.getPosition() != null && drop != null && drop.getPosition() != null
                && agent.getPosition().distanceSq(drop.getPosition())
                <= (long) AGENT_PICKUP_DISTANCE_PX * AGENT_PICKUP_DISTANCE_PX;
    }

    public static long rewardOpenedAt(EventInstanceManager event) {
        if (event == null) return 0L;
        String value = event.getProperty(REWARD_OPENED_AT_PROPERTY);
        if (value == null || value.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
