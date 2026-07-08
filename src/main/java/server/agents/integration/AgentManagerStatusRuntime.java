package server.agents.integration;

import client.Character;
import server.agents.capabilities.social.airshow.AgentAirshowStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned bridge for status callbacks from tick and lifecycle services.
 */
public final class AgentManagerStatusRuntime {
    private AgentManagerStatusRuntime() {
    }

    public static void scheduleSpawnStatusCheck(AgentRuntimeEntry entry, Character bot, long delayMs) {
        AgentSchedulerRuntime.afterDelay(delayMs, () -> checkManagerStatus(entry, bot));
    }

    public static void checkManagerStatus(AgentRuntimeEntry entry, Character bot) {
        AgentChatStatusRuntime.checkBotStatus(entry, bot);
    }

    public static void announceOwnerReturnedFromOffline(AgentRuntimeEntry entry) {
        AgentChatStatusRuntime.announceOwnerReturnedFromOffline(entry);
    }

    public static void tickAfkCheck(AgentRuntimeEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(entry, owner);
    }

    public static boolean airshowActive(AgentRuntimeEntry entry) {
        return AgentAirshowStateRuntime.active(entry);
    }
}
