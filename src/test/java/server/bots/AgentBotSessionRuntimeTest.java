package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSessionReplyRuntime;
import server.agents.integration.AgentBotSessionRuntime;
import server.agents.integration.AgentBotSessionSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentBotSessionRuntimeTest {
    @Test
    void relogRequestSchedulesStopPromptAndPendingAction() {
        BotEntry entry = new BotEntry(null, null, null);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<AgentBotSessionSchedulerRuntime> scheduler = mockStatic(AgentBotSessionSchedulerRuntime.class);
             MockedStatic<AgentBotSessionReplyRuntime> replies = mockStatic(AgentBotSessionReplyRuntime.class);
             MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            scheduler.when(() -> AgentBotSessionSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.sessionRequestCallbacks(entry).requestRelog();

            assertEquals(AgentChatPendingAction.RELOG, AgentBotPendingActionStateRuntime.pendingAction(entry));
            verify(manager).issueStop(entry);
            replies.verify(() -> AgentBotSessionReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void awayRequestPromptsFirstBotAndSetsPendingOwnerAway() {
        BotEntry entry = new BotEntry(null, null, null);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<AgentBotSessionSchedulerRuntime> scheduler = mockStatic(AgentBotSessionSchedulerRuntime.class);
             MockedStatic<AgentBotSessionReplyRuntime> replies = mockStatic(AgentBotSessionReplyRuntime.class);
             MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            when(manager.isFirstBotEntry(entry)).thenReturn(true);
            when(manager.shouldOfferTownForAwayCommand(entry)).thenReturn(true);
            scheduler.when(() -> AgentBotSessionSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.sessionRequestCallbacks(entry).requestAway();

            assertEquals(AgentChatPendingAction.OWNER_AWAY, AgentBotPendingActionStateRuntime.pendingAction(entry));
            verify(manager).issueStop(entry);
            replies.verify(() -> AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrLogoutPrompt()));
        }
    }

    @Test
    void awayChoiceStayClearsPendingActionAndKeepsAgentsNearOwner() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);
        BotEntry entry = new BotEntry(null, owner, null);
        entry.setPendingAction(AgentChatPendingAction.OWNER_AWAY);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<AgentBotSessionSchedulerRuntime> scheduler = mockStatic(AgentBotSessionSchedulerRuntime.class);
             MockedStatic<AgentBotSessionReplyRuntime> replies = mockStatic(AgentBotSessionReplyRuntime.class);
             MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            when(manager.shouldOfferTownForAwayCommand(entry)).thenReturn(false);
            scheduler.when(() -> AgentBotSessionSchedulerRuntime.afterRandomDelay(eq(700), eq(900), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.handleOwnerAwayChoice(entry, "stay");

            assertNull(AgentBotPendingActionStateRuntime.pendingAction(entry));
            verify(manager).issueOwnerAwaySafeModeForOwner(123, false);
            replies.verify(() -> AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayConfirmReply()));
        }
    }

    @Test
    void sessionReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotSessionReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void sessionSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSessionSchedulerRuntime.afterRandomDelay(900, 1100, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
