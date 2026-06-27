package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.integration.AgentBotActiveModeRuntime;
import server.agents.integration.AgentBotMovementReplyRuntime;
import server.agents.integration.AgentBotMovementRuntime;
import server.agents.integration.AgentBotMovementSchedulerRuntime;
import server.agents.integration.AgentBotMovementStatusRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

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
        BotEntry entry = new BotEntry(null, owner, null);
        BotManager manager = mock(BotManager.class);
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);

        try (MockedStatic<AgentBotMovementSchedulerRuntime> scheduler =
                     mockStatic(AgentBotMovementSchedulerRuntime.class);
             MockedStatic<AgentBotMovementStatusRuntime> status = mockStatic(AgentBotMovementStatusRuntime.class);
             MockedStatic<AgentBotMovementReplyRuntime> replies = mockStatic(AgentBotMovementReplyRuntime.class);
             MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            scheduler.when(() -> AgentBotMovementSchedulerRuntime.afterRandomDelay(eq(1000), eq(1500), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            assertTrue(AgentBotMovementRuntime.movementCallbacks(entry).farmHere());

            status.verify(() -> AgentBotMovementStatusRuntime.prepareMovementActiveMode(entry));
            verify(manager).issueFarmHere(eq(entry), pointCaptor.capture());
            assertEquals(new Point(10, 20), pointCaptor.getValue());
            replies.verify(() -> AgentBotMovementReplyRuntime.replyNow(eq(entry), anyString()));
        }
    }

    @Test
    void moveHereReturnsFalseWithoutOwnerPosition() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotMovementSchedulerRuntime> scheduler =
                     mockStatic(AgentBotMovementSchedulerRuntime.class)) {
            assertFalse(AgentBotMovementRuntime.movementCallbacks(entry).moveHere());

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void followQueuesReplySupplyCheckAndNestedFollowCommand() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<AgentBotMovementSchedulerRuntime> scheduler =
                     mockStatic(AgentBotMovementSchedulerRuntime.class);
             MockedStatic<AgentBotActiveModeRuntime> activeMode = mockStatic(AgentBotActiveModeRuntime.class);
             MockedStatic<AgentBotMovementReplyRuntime> replies = mockStatic(AgentBotMovementReplyRuntime.class);
             MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class);
             MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            scheduler.when(() -> AgentBotMovementSchedulerRuntime.afterRandomDelay(eq(1500), eq(2000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });
            scheduler.when(() -> AgentBotMovementSchedulerRuntime.afterRandomDelay(eq(250), eq(750), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotMovementRuntime.movementCallbacks(entry).follow();

            activeMode.verify(() -> AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry));
            replies.verify(() -> AgentBotMovementReplyRuntime.replyNow(eq(entry), anyString()));
            potions.verify(() -> BotPotionManager.checkPotShareOnModeStart(entry, bot));
            verify(manager).issueFollowOwner(entry);
        }
    }

    @Test
    void greetingAppliesHappyFaceFidgetReplyAndStatusCheck() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotMovementSchedulerRuntime> scheduler =
                     mockStatic(AgentBotMovementSchedulerRuntime.class);
             MockedStatic<BotFidgetSideEffects> fidgets = mockStatic(BotFidgetSideEffects.class);
             MockedStatic<AgentBotMovementReplyRuntime> replies = mockStatic(AgentBotMovementReplyRuntime.class);
             MockedStatic<AgentBotMovementStatusRuntime> status = mockStatic(AgentBotMovementStatusRuntime.class)) {
            scheduler.when(() -> AgentBotMovementSchedulerRuntime.afterRandomDelay(eq(900), eq(1100), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotMovementRuntime.movementCallbacks(entry).greeting();

            verify(bot).changeFaceExpression(Emote.HAPPY.getValue());
            fidgets.verify(() -> BotFidgetSideEffects.maybeStartGreetingFidget(eq(entry), anyInt()));
            replies.verify(() -> AgentBotMovementReplyRuntime.queueReply(eq(entry), anyString()));
            status.verify(() -> AgentBotMovementStatusRuntime.checkMovementStatus(entry, bot));
        }
    }

    @Test
    void movementReplyAdapterDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotMovementReplyRuntime.replyNow(entry, "reply");
            AgentBotMovementReplyRuntime.queueReply(entry, "queued");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotReplyRuntime.queueReply(entry, "queued"));
        }
    }

    @Test
    void movementSchedulerAdapterDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotMovementSchedulerRuntime.afterRandomDelay(900, 1100, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
