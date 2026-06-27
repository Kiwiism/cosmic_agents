package server.bots;

import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.integration.AgentBotSessionRuntime;

public final class BotChatSessionRuntime {
    private BotChatSessionRuntime() {
    }

    static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(BotEntry entry) {
        return AgentBotSessionRuntime.sessionRequestCallbacks(entry);
    }

    public static void scheduleRelogConfirm(BotEntry entry) {
        AgentBotSessionRuntime.scheduleRelogConfirm(entry);
    }

    public static void scheduleLogoutConfirm(BotEntry entry) {
        AgentBotSessionRuntime.scheduleLogoutConfirm(entry);
    }

    public static void handleOwnerAwayChoice(BotEntry entry, String message) {
        AgentBotSessionRuntime.handleOwnerAwayChoice(entry, message);
    }
}
