package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.agents.integration.AgentBotPendingActionRuntime;
import server.agents.integration.AgentBotPendingActionReplyRuntime;
import server.agents.integration.AgentBotPendingActionSchedulerRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
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
    void itemChoiceCallbacksScheduleLegacyInventoryChoice() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentPendingChatActionFlow.PendingActionCallbacks callbacks =
                AgentBotPendingActionRuntime.pendingActionCallbacks(entry);

        try (MockedStatic<AgentBotPendingActionSchedulerRuntime> scheduler =
                     mockStatic(AgentBotPendingActionSchedulerRuntime.class);
             MockedStatic<BotInventoryManager> inventory = mockStatic(BotInventoryManager.class)) {
            scheduler.when(() -> AgentBotPendingActionSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            callbacks.executeItemChoice("scrolls", true);

            inventory.verify(() -> BotInventoryManager.executeChoice("scrolls", true, entry, null));
        }
    }

    @Test
    void cancelItemChoiceSchedulesLegacyReply() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotPendingActionSchedulerRuntime> scheduler =
                     mockStatic(AgentBotPendingActionSchedulerRuntime.class);
             MockedStatic<AgentBotPendingActionReplyRuntime> replies =
                     mockStatic(AgentBotPendingActionReplyRuntime.class)) {
            scheduler.when(() -> AgentBotPendingActionSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotPendingActionRuntime.pendingActionCallbacks(entry).cancelItemChoice();

            replies.verify(() -> AgentBotPendingActionReplyRuntime.replyNow(
                    entry,
                    AgentPendingChatActionFlow.keepDropChoiceReply()));
        }
    }

    @Test
    void skillReportDecisionMutatesPendingActionAndQueuesReplies() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setMessageSending(true);
        AgentSkillReportFlow.SkillReportDecision decision =
                new AgentSkillReportFlow.SkillReportDecision(List.of("pick tree"), true, true);

        AgentBotPendingActionRuntime.applySkillReportDecision(entry, decision);

        assertEquals(AgentChatPendingAction.SKILL_TREE_CHOICE, AgentBotPendingActionStateRuntime.pendingAction(entry));
        assertEquals("pick tree", entry.messageQueue().peek().text());
    }

    @Test
    void pendingActionReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotPendingActionReplyRuntime.replyNow(entry, "now");
            AgentBotPendingActionReplyRuntime.queueReply(entry, "queued");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "now"));
            replies.verify(() -> AgentBotReplyRuntime.queueReply(entry, "queued"));
        }
    }

    @Test
    void pendingActionSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotPendingActionSchedulerRuntime.afterRandomDelay(400, 600, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(400, 600, action));
        }
    }
}
