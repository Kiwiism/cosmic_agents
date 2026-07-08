package server.agents.integration;

import server.agents.capabilities.supplies.AgentPotionService;

import server.agents.capabilities.dialogue.AgentEmote;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.integration.AgentActiveModeRuntime;
import server.agents.integration.AgentFidgetSideEffects;
import server.agents.integration.AgentMovementCommandRuntime;
import server.agents.integration.AgentMovementRuntime;
import server.agents.integration.AgentMovementStatusRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMovementRuntimeTest {
    @Test
    void farmHereSchedulesActiveModeAndFarmCommandAtOwnerPosition() {
        Character owner = mock(Character.class);
        when(owner.getPosition()).thenReturn(new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentMovementStatusRuntime> status = mockStatic(AgentMovementStatusRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentMovementCommandRuntime> movementCommands =
                     mockStatic(AgentMovementCommandRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(1000), eq(1500), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            assertTrue(AgentMovementRuntime.movementCallbacks(entry).farmHere());

            status.verify(() -> AgentMovementStatusRuntime.prepareMovementActiveMode(entry));
            movementCommands.verify(() -> AgentMovementCommandRuntime.farmHere(
                    (AgentRuntimeEntry) eq(entry),
                    pointCaptor.capture()));
            assertEquals(new Point(10, 20), pointCaptor.getValue());
            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void moveHereReturnsFalseWithoutOwnerPosition() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            assertFalse(AgentMovementRuntime.movementCallbacks(entry).moveHere());

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void followQueuesReplySupplyCheckAndNestedFollowCommand() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentActiveModeRuntime> activeMode = mockStatic(AgentActiveModeRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<AgentMovementCommandRuntime> movementCommands =
                     mockStatic(AgentMovementCommandRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(1500), eq(2000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(250), eq(750), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentMovementRuntime.movementCallbacks(entry).follow();

            activeMode.verify(() -> AgentActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry));
            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), anyString()));
            potions.verify(() -> AgentPotionService.checkPotShareOnModeStart((AgentRuntimeEntry) eq(entry), eq(bot)));
            movementCommands.verify(() -> AgentMovementCommandRuntime.followOwner(entry));
        }
    }

    @Test
    void greetingAppliesHappyFaceFidgetReplyAndStatusCheck() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentFidgetSideEffects> fidgets = mockStatic(AgentFidgetSideEffects.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentMovementStatusRuntime> status = mockStatic(AgentMovementStatusRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentMovementRuntime.movementCallbacks(entry).greeting();

            verify(bot).changeFaceExpression(AgentEmote.HAPPY.getValue());
            fidgets.verify(() -> AgentFidgetSideEffects.maybeStartGreetingFidget(eq(entry), anyInt()));
            replies.verify(() -> AgentReplyRuntime.queueReply(eq(entry), anyString()));
            status.verify(() -> AgentMovementStatusRuntime.checkMovementStatus(entry, bot));
        }
    }
}
