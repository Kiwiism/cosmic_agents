package server.agents.capabilities.dialogue;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentSessionRuntime;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.runtime.AgentPendingActionStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;

/**
 * Agent-owned pending-action facade over live Agent state adapters. Session,
 * reply, scheduling, and inventory transfer side effects remain delegated.
 */
public final class AgentPendingActionRuntime {
    private AgentPendingActionRuntime() {
    }

    public static AgentPendingChatActionFlow.PendingActionState pendingActionState(AgentRuntimeEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionState() {
            @Override
            public String pendingAction() {
                return AgentPendingActionStateRuntime.pendingAction(entry);
            }

            @Override
            public String pendingDropCategory() {
                return AgentPendingActionStateRuntime.pendingDropCategory(entry);
            }

            @Override
            public void clearPendingAction() {
                AgentPendingActionStateRuntime.clearPendingAction(entry);
            }

            @Override
            public void clearPendingDropCategory() {
                AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
            }
        };
    }

    public static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(AgentRuntimeEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                AgentSessionRuntime.handleOwnerAwayChoice(entry, message);
            }

            @Override
            public void executeItemChoice(String category, boolean trade) {
                AgentSchedulerRuntime.afterRandomDelay(400, 600,
                        () -> AgentInventoryTransferService.executeChoice(
                                category, trade, entry, AgentRuntimeIdentityRuntime.bot(entry)));
            }

            @Override
            public void cancelItemChoice() {
                AgentSchedulerRuntime.afterRandomDelay(400, 600,
                        () -> AgentReplyRuntime.replyNow(
                                entry,
                                AgentPendingChatActionFlow.keepDropChoiceReply()));
            }

            @Override
            public void handleSkillTreeChoice(String message) {
                AgentPendingActionRuntime.handleSkillTreeChoice(
                        entry, AgentRuntimeIdentityRuntime.bot(entry), message);
            }

            @Override
            public void confirmRelog() {
                AgentSessionRuntime.scheduleRelogConfirm(entry);
            }

            @Override
            public void confirmLogout() {
                AgentSessionRuntime.scheduleLogoutConfirm(entry);
            }

            @Override
            public void cancelPendingAction(boolean dropAction) {
                String cancelMsg = AgentPendingChatActionFlow.pendingActionCancelReply(dropAction);
                AgentSchedulerRuntime.afterRandomDelay(700, 900,
                        () -> AgentReplyRuntime.replyNow(entry, cancelMsg));
            }
        };
    }

    public static void handleSkillTreeChoice(AgentRuntimeEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    public static void applySkillReportDecision(AgentRuntimeEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        if (decision.clearPendingAction()) {
            AgentPendingActionStateRuntime.clearPendingAction(entry);
        }
        if (decision.requestSkillTreeChoice()) {
            AgentPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.SKILL_TREE_CHOICE);
        }
        for (String line : decision.replies()) {
            AgentReplyRuntime.queueReply(entry, line);
        }
    }
}
