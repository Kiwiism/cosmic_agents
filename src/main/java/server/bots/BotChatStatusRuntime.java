package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.integration.AgentBotActiveModeRuntime;
import server.agents.integration.AgentBotBuildStatusRuntime;
import server.agents.integration.AgentBotStatusRuntime;

final class BotChatStatusRuntime {
    private BotChatStatusRuntime() {
    }

    static void markOwnerActive(BotEntry entry) {
        Character owner = entry.owner;
        AgentChatStatusRuntime.markActive(
                AgentBotStatusRuntime.statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    static void checkBotStatus(BotEntry entry, Character bot) {
        AgentChatStatusRuntime.checkStatus(
                AgentBotStatusRuntime.statusCheckState(entry),
                AgentBotBuildStatusRuntime.statusCheckActions(entry, bot));
    }

    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        final Character bot = entry.bot;
        AgentChatStatusRuntime.announceOfflineReturn(AgentBotStatusRuntime.offlineReturnActions(bot));
    }

    static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(
                AgentBotStatusRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                AgentBotStatusRuntime.afkReturnActions(entry));
    }

    static void prepareActiveModeEntry(BotEntry entry) {
        AgentChatStatusRuntime.prepareActiveMode(AgentBotActiveModeRuntime.activeModeActions(entry));
    }

    static boolean isOwnerIdle(BotEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry));
    }

    static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }

}
