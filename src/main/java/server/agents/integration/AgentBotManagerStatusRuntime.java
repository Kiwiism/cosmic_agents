package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for status callbacks still triggered by the
 * legacy BotManager tick and lifecycle shell.
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

    public static void announceOwnerReturnedFromOffline(BotEntry entry) {
        AgentBotChatStatusRuntime.announceOwnerReturnedFromOffline(entry);
    }

    public static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentBotChatStatusRuntime.tickAfkCheck(entry, owner);
    }
}
