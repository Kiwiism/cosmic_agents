package server.bots;


import client.Character;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.agents.integration.AgentBotPendingActionRuntime;

/**
 * Temporary bot-side pending-action adapter while pending action side effects
 * still mutate bot runtime state directly.
 */
public final class BotChatPendingActionRuntime {
    private BotChatPendingActionRuntime() {
    }

    static AgentPendingChatActionFlow.PendingActionState pendingActionState(BotEntry entry) {
        return AgentBotPendingActionRuntime.pendingActionState(entry);
    }

    static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return AgentBotPendingActionRuntime.pendingActionCallbacks(entry);
    }

    static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        AgentBotPendingActionRuntime.handleSkillTreeChoice(entry, bot, message);
    }

    public static void applySkillReportDecision(BotEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        AgentBotPendingActionRuntime.applySkillReportDecision(entry, decision);
    }
}
