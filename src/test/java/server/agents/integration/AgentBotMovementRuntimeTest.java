package server.agents.integration;

import server.agents.capabilities.supplies.AgentPotionService;

import server.agents.capabilities.dialogue.AgentEmote;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.integration.AgentBotActiveModeRuntime;
import server.agents.integration.AgentBotFidgetSideEffects;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentBotMovementRuntime;
import server.agents.integration.AgentBotMovementStatusRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
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

class AgentBotMovementRuntimeTest {
    @Test
    void farmHereSchedulesActiveModeAndFarmCommandAtOwnerPosition() {
        Character owner = mock(Character.class);
        when(owner.getPosition()).thenReturn(new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotMovementStatusRuntime> status = mockStatic(AgentBotMovementStatusRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotMovementCommandRuntime> movementCommands =
                     mockStatic(AgentBotMovementCommandRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(1000), eq(1500), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            assertTrue(AgentBotMovementRuntime.movementCallbacks(entry).farmHere());

            status.verify(() -> AgentBotMovementStatusRuntime.prepareMovementActiveMode(entry));
            movementCommands.verify(() -> AgentBotMovementCommandRuntime.farmHere(
                    (AgentRuntimeEntry) eq(entry),
                    pointCaptor.capture()));
            assertEquals(new Point(10, 20), pointCaptor.getValue());
            replies.verify(() -> AgentBotReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void moveHereReturnsFalseWithoutOwnerPosition() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class)) {
            assertFalse(AgentBotMovementRuntime.movementCallbacks(entry).moveHere());

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void followQueuesReplySupplyCheckAndNestedFollowCommand() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotActiveModeRuntime> activeMode = mockStatic(AgentBotActiveModeRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<AgentBotMovementCommandRuntime> movementCommands =
                     mockStatic(AgentBotMovementCommandRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(1500), eq(2000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(250), eq(750), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotMovementRuntime.movementCallbacks(entry).follow();

            activeMode.verify(() -> AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(eq(entry), anyString()));
            potions.verify(() -> AgentPotionService.checkPotShareOnModeStart((AgentRuntimeEntry) eq(entry), eq(bot)));
            movementCommands.verify(() -> AgentBotMovementCommandRuntime.followOwner(entry));
        }
    }

    @Test
    void greetingAppliesHappyFaceFidgetReplyAndStatusCheck() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotFidgetSideEffects> fidgets = mockStatic(AgentBotFidgetSideEffects.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotMovementStatusRuntime> status = mockStatic(AgentBotMovementStatusRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotMovementRuntime.movementCallbacks(entry).greeting();

            verify(bot).changeFaceExpression(AgentEmote.HAPPY.getValue());
            fidgets.verify(() -> AgentBotFidgetSideEffects.maybeStartGreetingFidget(eq(entry), anyInt()));
            replies.verify(() -> AgentBotReplyRuntime.queueReply(eq(entry), anyString()));
            status.verify(() -> AgentBotMovementStatusRuntime.checkMovementStatus(entry, bot));
        }
    }
}
