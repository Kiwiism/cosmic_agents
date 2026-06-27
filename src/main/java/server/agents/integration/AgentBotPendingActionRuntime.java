package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.bots.BotChatSessionRuntime;
import server.bots.BotEntry;
import server.bots.BotInventoryManager;
import server.bots.BotManager;

import java.util.List;
import java.util.Map;

/**
 * Agent-owned pending-action facade over temporary bot-side state and side
 * effects.
 */
public final class AgentBotPendingActionRuntime {
    private AgentBotPendingActionRuntime() {
    }

    public static AgentPendingChatActionFlow.PendingActionState pendingActionState(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionState() {
            @Override
            public String pendingAction() {
                return entry.pendingAction();
            }

            @Override
            public String pendingDropCategory() {
                return entry.pendingDropCategory();
            }

            @Override
            public void clearPendingAction() {
                entry.clearPendingAction();
            }

            @Override
            public void clearPendingDropCategory() {
                entry.clearPendingDropCategory();
            }
        };
    }

    public static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                BotChatSessionRuntime.handleOwnerAwayChoice(entry, message);
            }

            @Override
            public void executeItemChoice(String category, boolean trade) {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600,
                        () -> BotInventoryManager.executeChoice(category, trade, entry, entry.bot()));
            }

            @Override
            public void cancelItemChoice() {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600,
                        () -> BotManager.getInstance().botReply(entry, AgentPendingChatActionFlow.keepDropChoiceReply()));
            }

            @Override
            public void handleSkillTreeChoice(String message) {
                AgentBotPendingActionRuntime.handleSkillTreeChoice(entry, entry.bot(), message);
            }

            @Override
            public void confirmRelog() {
                BotChatSessionRuntime.scheduleRelogConfirm(entry);
            }

            @Override
            public void confirmLogout() {
                BotChatSessionRuntime.scheduleLogoutConfirm(entry);
            }

            @Override
            public void cancelPendingAction(boolean dropAction) {
                String cancelMsg = AgentPendingChatActionFlow.pendingActionCancelReply(dropAction);
                AgentBotSchedulerRuntime.afterRandomDelay(700, 900,
                        () -> BotManager.getInstance().botReply(entry, cancelMsg));
            }
        };
    }

    public static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    public static void applySkillReportDecision(BotEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        if (decision.clearPendingAction()) {
            entry.clearPendingAction();
        }
        if (decision.requestSkillTreeChoice()) {
            entry.setPendingAction(AgentChatPendingAction.SKILL_TREE_CHOICE);
        }
        for (String line : decision.replies()) {
            AgentBotReplyRuntime.queueReply(entry, line);
        }
    }
}
