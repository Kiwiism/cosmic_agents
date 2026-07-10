package server.agents.capabilities.dialogue;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.commands.AgentMessageQueueStateRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class AgentPendingActionRuntimeTest {
    @Test
    void pendingActionStateAdaptsAgentRuntimeEntryFields() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPendingActionStateRuntime.setPendingAction(entry, "drop_scrolls");
        AgentPendingActionStateRuntime.setPendingDropCategory(entry, "scrolls");

        AgentPendingChatActionFlow.PendingActionState state =
                AgentPendingActionRuntime.pendingActionState(entry);

        assertEquals("drop_scrolls", state.pendingAction());
        assertEquals("scrolls", state.pendingDropCategory());

        state.clearPendingAction();
        state.clearPendingDropCategory();

        assertNull(AgentPendingActionStateRuntime.pendingAction(entry));
        assertNull(AgentPendingActionStateRuntime.pendingDropCategory(entry));
    }

    @Test
    void itemChoiceCallbacksScheduleAgentInventoryChoice() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPendingChatActionFlow.PendingActionCallbacks callbacks =
                AgentPendingActionRuntime.pendingActionCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentInventoryTransferService> inventory = mockStatic(AgentInventoryTransferService.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            callbacks.executeItemChoice("scrolls", true);

            inventory.verify(() -> AgentInventoryTransferService.executeChoice("scrolls", true, entry, null));
        }
    }

    @Test
    void cancelItemChoiceSchedulesLegacyReply() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentReplyRuntime> replies =
                     mockStatic(AgentReplyRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentPendingActionRuntime.pendingActionCallbacks(entry).cancelItemChoice();

            replies.verify(() -> AgentReplyRuntime.replyNow(
                    entry,
                    AgentPendingChatActionFlow.keepDropChoiceReply()));
        }
    }

    @Test
    void skillReportDecisionMutatesPendingActionAndQueuesReplies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMessageQueueStateRuntime.setSending(entry, true);
        AgentSkillReportFlow.SkillReportDecision decision =
                new AgentSkillReportFlow.SkillReportDecision(List.of("pick tree"), true, true);

        AgentPendingActionRuntime.applySkillReportDecision(entry, decision);

        assertEquals(AgentChatPendingAction.SKILL_TREE_CHOICE, AgentPendingActionStateRuntime.pendingAction(entry));
        assertEquals("pick tree", AgentMessageQueueStateRuntime.peek(entry).text());
    }

    @Test
    void broadReplyRuntimeStillSupportsPendingActionReplies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentReplyRuntime.replyNow(entry, "now");
            AgentReplyRuntime.queueReply(entry, "queued");

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "now"));
            replies.verify(() -> AgentReplyRuntime.queueReply(entry, "queued"));
        }
    }

    @Test
    void broadSchedulerRuntimeStillSupportsPendingActionDelays() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentSchedulerRuntime.afterRandomDelay(400, 600, action);

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(400, 600, action));
        }
    }
}
