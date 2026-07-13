package server.agents.commands;

import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;
import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentSessionControlRuntime;
import server.agents.runtime.AgentSessionLifecycleRuntime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentSessionCommandCoordinatorTest {
    @AfterEach
    void clearSchedulerMode() {
        System.clearProperty("agents.scheduler.mode");
    }

    @Test
    void relogRequestSchedulesStopPromptAndPendingAction() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentMovementCommandRuntime> movementCommands = mockStatic(AgentMovementCommandRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentSessionCommandCoordinator.sessionRequestCallbacks(entry).requestRelog();

            assertEquals(AgentChatPendingAction.RELOG, AgentPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void awayRequestPromptsFirstBotAndSetsPendingOwnerAway() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentMovementCommandRuntime> movementCommands = mockStatic(AgentMovementCommandRuntime.class);
             MockedStatic<AgentSessionControlRuntime> sessionControl = mockStatic(AgentSessionControlRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            sessionControl.when(() -> AgentSessionControlRuntime.isPrimarySession(entry)).thenReturn(true);
            sessionControl.when(() -> AgentSessionControlRuntime.shouldOfferTownForAwayCommand(entry)).thenReturn(true);
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentSessionCommandCoordinator.sessionRequestCallbacks(entry).requestAway();

            assertEquals(AgentChatPendingAction.OWNER_AWAY, AgentPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
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
             MockedStatic<AgentSessionControlRuntime> sessionControl = mockStatic(AgentSessionControlRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            sessionControl.when(() -> AgentSessionControlRuntime.shouldOfferTownForAwayCommand(entry)).thenReturn(false);
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(700), eq(900), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentSessionCommandCoordinator.handleOwnerAwayChoice(entry, "stay");

            assertNull(AgentPendingActionStateRuntime.pendingAction(entry));
            sessionControl.verify(() -> AgentSessionControlRuntime.issueOwnerAwaySafeModeForLeader(123, false));
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

    @Test
    void centralLogoutDispatchesEachSiblingStopThroughItsMailbox() {
        System.setProperty("agents.scheduler.mode", "central-sequential");
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);
        AgentRuntimeEntry first = new AgentRuntimeEntry(mock(Character.class), owner, null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(mock(Character.class), owner, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentMovementCommandRuntime> movementCommands = mockStatic(AgentMovementCommandRuntime.class);
             MockedStatic<AgentSessionControlRuntime> sessionControl = mockStatic(AgentSessionControlRuntime.class);
             MockedStatic<AgentSessionLifecycleRuntime> lifecycle = mockStatic(AgentSessionLifecycleRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            sessionControl.when(() -> AgentSessionControlRuntime.shouldOfferTownForAwayCommand(first)).thenReturn(true);
            lifecycle.when(() -> AgentSessionLifecycleRuntime.getBotEntries(123)).thenReturn(List.of(first, second));
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(first), eq(700), eq(900), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentSessionCommandCoordinator.handleOwnerAwayChoice(first, "logout");

            movementCommands.verifyNoInteractions();
            assertEquals(1, first.actionMailbox().drain(first, 8));
            assertEquals(1, second.actionMailbox().drain(second, 8));
            movementCommands.verify(() -> AgentMovementCommandRuntime.stop(first));
            movementCommands.verify(() -> AgentMovementCommandRuntime.stop(second));
        }
    }
}
