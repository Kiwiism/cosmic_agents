package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;

import java.util.List;
import java.util.Map;

/**
 * Temporary bot-side pending-action adapter while pending action side effects
 * still mutate bot runtime state directly.
 */
final class BotChatPendingActionRuntime {
    private BotChatPendingActionRuntime() {
    }

    static AgentPendingChatActionFlow.PendingActionState pendingActionState(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionState() {
            @Override
            public String pendingAction() {
                return entry.pendingAction;
            }

            @Override
            public String pendingDropCategory() {
                return entry.pendingDropCategory;
            }

            @Override
            public void clearPendingAction() {
                entry.pendingAction = null;
            }

            @Override
            public void clearPendingDropCategory() {
                entry.pendingDropCategory = null;
            }
        };
    }

    static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                BotChatSessionRuntime.handleOwnerAwayChoice(entry, message);
            }

            @Override
            public void executeItemChoice(String category, boolean trade) {
                BotManager.after(BotManager.randMs(400, 600),
                        () -> BotInventoryManager.executeChoice(category, trade, entry, entry.bot));
            }

            @Override
            public void cancelItemChoice() {
                BotManager.after(BotManager.randMs(400, 600),
                        () -> BotManager.getInstance().botReply(entry, AgentPendingChatActionFlow.keepDropChoiceReply()));
            }

            @Override
            public void handleSkillTreeChoice(String message) {
                BotChatPendingActionRuntime.handleSkillTreeChoice(entry, entry.bot, message);
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
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, cancelMsg));
            }
        };
    }

    static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    static void applySkillReportDecision(BotEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        if (decision.clearPendingAction()) {
            entry.pendingAction = null;
        }
        if (decision.requestSkillTreeChoice()) {
            entry.pendingAction = AgentChatPendingAction.SKILL_TREE_CHOICE;
        }
        for (String line : decision.replies()) {
            AgentBotReplyRuntime.queueReply(entry, line);
        }
    }
}
