package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.agents.integration.AgentBotPendingActionRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentBotPendingActionRuntimeTest {
    @Test
    void pendingActionStateAdaptsBotEntryFields() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setPendingAction("drop_scrolls");
        entry.pendingDropCategory = "scrolls";

        AgentPendingChatActionFlow.PendingActionState state =
                AgentBotPendingActionRuntime.pendingActionState(entry);

        assertEquals("drop_scrolls", state.pendingAction());
        assertEquals("scrolls", state.pendingDropCategory());

        state.clearPendingAction();
        state.clearPendingDropCategory();

        assertNull(entry.pendingAction());
        assertNull(entry.pendingDropCategory());
    }

    @Test
    void itemChoiceCallbacksScheduleLegacyInventoryChoice() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentPendingChatActionFlow.PendingActionCallbacks callbacks =
                AgentBotPendingActionRuntime.pendingActionCallbacks(entry);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<BotInventoryManager> inventory = mockStatic(BotInventoryManager.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)))
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
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotPendingActionRuntime.pendingActionCallbacks(entry).cancelItemChoice();

            verify(manager).botReply(entry, AgentPendingChatActionFlow.keepDropChoiceReply());
        }
    }

    @Test
    void skillReportDecisionMutatesPendingActionAndQueuesReplies() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setMessageSending(true);
        AgentSkillReportFlow.SkillReportDecision decision =
                new AgentSkillReportFlow.SkillReportDecision(List.of("pick tree"), true, true);

        AgentBotPendingActionRuntime.applySkillReportDecision(entry, decision);

        assertEquals(AgentChatPendingAction.SKILL_TREE_CHOICE, entry.pendingAction());
        assertEquals("pick tree", entry.messageQueue().peek().text());
    }
}
