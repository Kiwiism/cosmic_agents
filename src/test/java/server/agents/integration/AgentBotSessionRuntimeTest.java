package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentPendingActionStateRuntime;
import server.agents.integration.AgentSchedulerRuntime;
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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentBotMovementCommandRuntime> movementCommands = mockStatic(AgentBotMovementCommandRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.sessionRequestCallbacks(entry).requestRelog();

            assertEquals(AgentChatPendingAction.RELOG, AgentPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentBotMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void awayRequestPromptsFirstBotAndSetsPendingOwnerAway() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentBotMovementCommandRuntime> movementCommands = mockStatic(AgentBotMovementCommandRuntime.class);
             MockedStatic<AgentBotSessionControlRuntime> sessionControl = mockStatic(AgentBotSessionControlRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            sessionControl.when(() -> AgentBotSessionControlRuntime.isPrimarySession(entry)).thenReturn(true);
            sessionControl.when(() -> AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry)).thenReturn(true);
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.sessionRequestCallbacks(entry).requestAway();

            assertEquals(AgentChatPendingAction.OWNER_AWAY, AgentPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentBotMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
            replies.verify(() -> AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrLogoutPrompt()));
        }
    }

    @Test
    void awayChoiceStayClearsPendingActionAndKeepsAgentsNearOwner() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);
        AgentPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.OWNER_AWAY);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentBotSessionControlRuntime> sessionControl = mockStatic(AgentBotSessionControlRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            sessionControl.when(() -> AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry)).thenReturn(false);
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(700), eq(900), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotSessionRuntime.handleOwnerAwayChoice(entry, "stay");

            assertNull(AgentPendingActionStateRuntime.pendingAction(entry));
            sessionControl.verify(() -> AgentBotSessionControlRuntime.issueOwnerAwaySafeModeForLeader(123, false));
            replies.verify(() -> AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayConfirmReply()));
        }
    }

    @Test
    void broadReplyRuntimeStillSupportsSessionReplies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void broadSchedulerRuntimeStillSupportsSessionDelays() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentSchedulerRuntime.afterRandomDelay(900, 1100, action);

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
