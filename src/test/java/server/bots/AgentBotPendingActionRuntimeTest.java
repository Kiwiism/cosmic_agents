package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.integration.AgentBotPendingActionRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.integration.AgentBotMessageQueueStateRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class AgentBotPendingActionRuntimeTest {
    @Test
    void pendingActionStateAdaptsBotEntryFields() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotPendingActionStateRuntime.setPendingAction(entry, "drop_scrolls");
        AgentBotPendingActionStateRuntime.setPendingDropCategory(entry, "scrolls");

        AgentPendingChatActionFlow.PendingActionState state =
                AgentBotPendingActionRuntime.pendingActionState(entry);

        assertEquals("drop_scrolls", state.pendingAction());
        assertEquals("scrolls", state.pendingDropCategory());

        state.clearPendingAction();
        state.clearPendingDropCategory();

        assertNull(AgentBotPendingActionStateRuntime.pendingAction(entry));
        assertNull(AgentBotPendingActionStateRuntime.pendingDropCategory(entry));
    }

    @Test
    void itemChoiceCallbacksScheduleAgentInventoryChoice() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentPendingChatActionFlow.PendingActionCallbacks callbacks =
                AgentBotPendingActionRuntime.pendingActionCallbacks(entry);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentInventoryTransferService> inventory = mockStatic(AgentInventoryTransferService.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            callbacks.executeItemChoice("scrolls", true);

            inventory.verify(() -> AgentInventoryTransferService.executeChoice("scrolls", true, entry, null));
        }
    }

    @Test
    void cancelItemChoiceSchedulesLegacyReply() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies =
                     mockStatic(AgentBotReplyRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotPendingActionRuntime.pendingActionCallbacks(entry).cancelItemChoice();

            replies.verify(() -> AgentBotReplyRuntime.replyNow(
                    entry,
                    AgentPendingChatActionFlow.keepDropChoiceReply()));
        }
    }

    @Test
    void skillReportDecisionMutatesPendingActionAndQueuesReplies() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMessageQueueStateRuntime.setSending(entry, true);
        AgentSkillReportFlow.SkillReportDecision decision =
                new AgentSkillReportFlow.SkillReportDecision(List.of("pick tree"), true, true);

        AgentBotPendingActionRuntime.applySkillReportDecision(entry, decision);

        assertEquals(AgentChatPendingAction.SKILL_TREE_CHOICE, AgentBotPendingActionStateRuntime.pendingAction(entry));
        assertEquals("pick tree", AgentBotMessageQueueStateRuntime.peek(entry).text());
    }

    @Test
    void broadReplyRuntimeStillSupportsPendingActionReplies() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotReplyRuntime.replyNow(entry, "now");
            AgentBotReplyRuntime.queueReply(entry, "queued");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "now"));
            replies.verify(() -> AgentBotReplyRuntime.queueReply(entry, "queued"));
        }
    }

    @Test
    void broadSchedulerRuntimeStillSupportsPendingActionDelays() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSchedulerRuntime.afterRandomDelay(400, 600, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(400, 600, action));
        }
    }
}
