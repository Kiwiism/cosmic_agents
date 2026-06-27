package server.bots;

import client.Character;
import server.agents.integration.AgentBotChatStatusRuntime;

final class BotChatStatusRuntime {
    private BotChatStatusRuntime() {
    }

    static void markOwnerActive(BotEntry entry) {
        AgentBotChatStatusRuntime.markOwnerActive(entry);
    }

    static void checkBotStatus(BotEntry entry, Character bot) {
        AgentBotChatStatusRuntime.checkBotStatus(entry, bot);
    }

    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        AgentBotChatStatusRuntime.announceOwnerReturnedFromOffline(entry);
    }

    static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentBotChatStatusRuntime.tickAfkCheck(entry, owner);
    }

    static void prepareActiveModeEntry(BotEntry entry) {
        AgentBotChatStatusRuntime.prepareActiveModeEntry(entry);
    }

    static boolean isOwnerIdle(BotEntry entry) {
        return AgentBotChatStatusRuntime.isOwnerIdle(entry);
    }

    static int randomFidgetExpression() {
        return AgentBotChatStatusRuntime.randomFidgetExpression();
    }

}
