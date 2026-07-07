package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;

/**
 * Agent-owned bridge for status callbacks from tick and lifecycle services.
 */
public final class AgentBotManagerStatusRuntime {
    private AgentBotManagerStatusRuntime() {
    }

    public static void scheduleSpawnStatusCheck(BotEntry entry, Character bot, long delayMs) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, () -> checkManagerStatus(entry, bot));
    }

    public static void checkManagerStatus(BotEntry entry, Character bot) {
        AgentBotChatStatusRuntime.checkBotStatus(entry, bot);
    }

    public static void announceOwnerReturnedFromOffline(AgentRuntimeEntry entry) {
        AgentBotChatStatusRuntime.announceOwnerReturnedFromOffline(entry);
    }

    public static void tickAfkCheck(AgentRuntimeEntry entry, Character owner) {
        AgentBotChatStatusRuntime.tickAfkCheck(entry, owner);
    }

    public static boolean airshowActive(AgentRuntimeEntry entry) {
        return AgentBotAirshowStateRuntime.active(entry);
    }
}
