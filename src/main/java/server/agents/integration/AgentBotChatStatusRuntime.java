package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.bots.BotEntry;

/**
 * Agent-owned status facade over temporary bot-side status state and side
 * effects. Legacy bot package callers should delegate here until their call
 * sites move into Agent modules.
 */
public final class AgentBotChatStatusRuntime {
    private AgentBotChatStatusRuntime() {
    }

    public static void markOwnerActive(BotEntry entry) {
        Character owner = entry.owner();
        AgentChatStatusRuntime.markActive(
                AgentBotStatusRuntime.statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    public static void checkBotStatus(BotEntry entry, Character bot) {
        AgentChatStatusRuntime.checkStatus(
                AgentBotStatusRuntime.statusCheckState(entry),
                AgentBotBuildStatusRuntime.statusCheckActions(entry, bot));
    }

    public static void announceOwnerReturnedFromOffline(BotEntry entry) {
        AgentChatStatusRuntime.announceOfflineReturn(AgentBotStatusRuntime.offlineReturnActions(entry.bot()));
    }

    public static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(
                AgentBotStatusRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                AgentBotStatusRuntime.afkReturnActions(entry));
    }

    public static void prepareActiveModeEntry(BotEntry entry) {
        AgentChatStatusRuntime.prepareActiveMode(AgentBotActiveModeRuntime.activeModeActions(entry));
    }

    public static boolean isOwnerIdle(BotEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry));
    }

    public static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }
}
