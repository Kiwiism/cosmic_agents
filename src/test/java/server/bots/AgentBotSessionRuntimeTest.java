package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSessionControlRuntime;
import server.agents.integration.AgentBotSessionRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentBotSessionRuntimeTest {
    @Test
    void relogRequestSchedulesStopPromptAndPendingAction() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotMovementCommandRuntime> movementCommands = mockStatic(AgentBotMovementCommandRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.sessionRequestCallbacks(entry).requestRelog();

            assertEquals(AgentChatPendingAction.RELOG, AgentBotPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentBotMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void awayRequestPromptsFirstBotAndSetsPendingOwnerAway() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotMovementCommandRuntime> movementCommands = mockStatic(AgentBotMovementCommandRuntime.class);
             MockedStatic<AgentBotSessionControlRuntime> sessionControl = mockStatic(AgentBotSessionControlRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            sessionControl.when(() -> AgentBotSessionControlRuntime.isPrimarySession(entry)).thenReturn(true);
            sessionControl.when(() -> AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry)).thenReturn(true);
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.sessionRequestCallbacks(entry).requestAway();

            assertEquals(AgentChatPendingAction.OWNER_AWAY, AgentBotPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentBotMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrLogoutPrompt()));
        }
    }

    @Test
    void awayChoiceStayClearsPendingActionAndKeepsAgentsNearOwner() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);
        BotEntry entry = new BotEntry(null, owner, null);
        AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.OWNER_AWAY);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotSessionControlRuntime> sessionControl = mockStatic(AgentBotSessionControlRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            sessionControl.when(() -> AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry)).thenReturn(false);
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(700), eq(900), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.handleOwnerAwayChoice(entry, "stay");

            assertNull(AgentBotPendingActionStateRuntime.pendingAction(entry));
            sessionControl.verify(() -> AgentBotSessionControlRuntime.issueOwnerAwaySafeModeForLeader(123, false));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayConfirmReply()));
        }
    }

    @Test
    void broadReplyRuntimeStillSupportsSessionReplies() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void broadSchedulerRuntimeStillSupportsSessionDelays() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
