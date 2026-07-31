package server.agents.commands;

import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;
import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentSessionCommandCoordinatorTest {
    @Test
    void relogRequestSchedulesStopPromptAndPendingAction() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentMovementCommandRuntime> movementCommands = mockStatic(AgentMovementCommandRuntime.class);
             MockedStatic<AgentDialogueTransportRuntime> replies = mockStatic(AgentDialogueTransportRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentSessionCommandCoordinator.sessionRequestCallbacks(entry).requestRelog();

            assertEquals(AgentChatPendingAction.RELOG, AgentPendingActionStateRuntime.pendingAction(entry));
            movementCommands.verify(() -> AgentMovementCommandRuntime.stop((AgentRuntimeEntry) entry));
            replies.verify(() -> AgentDialogueTransportRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void broadReplyRuntimeStillSupportsSessionReplies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        try (MockedStatic<AgentDialogueTransportRuntime> replies = mockStatic(AgentDialogueTransportRuntime.class)) {
            AgentDialogueTransportRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentDialogueTransportRuntime.replyNow(entry, "reply"));
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
