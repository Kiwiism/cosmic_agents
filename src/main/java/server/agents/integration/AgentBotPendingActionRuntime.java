package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;

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
                return AgentBotPendingActionStateRuntime.pendingAction(entry);
            }

            @Override
            public String pendingDropCategory() {
                return AgentBotPendingActionStateRuntime.pendingDropCategory(entry);
            }

            @Override
            public void clearPendingAction() {
                AgentBotPendingActionStateRuntime.clearPendingAction(entry);
            }

            @Override
            public void clearPendingDropCategory() {
                AgentBotPendingActionStateRuntime.clearPendingDropCategory(entry);
            }
        };
    }

    public static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                AgentBotSessionRuntime.handleOwnerAwayChoice(entry, message);
            }

            @Override
            public void executeItemChoice(String category, boolean trade) {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600,
                        () -> AgentInventoryTransferService.executeChoice(
                                category, trade, entry, AgentBotRuntimeIdentityRuntime.bot(entry)));
            }

            @Override
            public void cancelItemChoice() {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600,
                        () -> AgentBotReplyRuntime.replyNow(
                                entry,
                                AgentPendingChatActionFlow.keepDropChoiceReply()));
            }

            @Override
            public void handleSkillTreeChoice(String message) {
                AgentBotPendingActionRuntime.handleSkillTreeChoice(
                        entry, AgentBotRuntimeIdentityRuntime.bot(entry), message);
            }

            @Override
            public void confirmRelog() {
                AgentBotSessionRuntime.scheduleRelogConfirm(entry);
            }

            @Override
            public void confirmLogout() {
                AgentBotSessionRuntime.scheduleLogoutConfirm(entry);
            }

            @Override
            public void cancelPendingAction(boolean dropAction) {
                String cancelMsg = AgentPendingChatActionFlow.pendingActionCancelReply(dropAction);
                AgentBotSchedulerRuntime.afterRandomDelay(700, 900,
                        () -> AgentBotReplyRuntime.replyNow(entry, cancelMsg));
            }
        };
    }

    public static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    public static void applySkillReportDecision(AgentRuntimeEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        if (decision.clearPendingAction()) {
            AgentBotPendingActionStateRuntime.clearPendingAction(entry);
        }
        if (decision.requestSkillTreeChoice()) {
            AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.SKILL_TREE_CHOICE);
        }
        for (String line : decision.replies()) {
            AgentBotReplyRuntime.queueReply(entry, line);
        }
    }
}
