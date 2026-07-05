package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotOfferRuntimeTest {
    @Test
    void recommendedGearActionsReportMissingOwner() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotOfferRuntime.recommendedGearActions(entry, null, null).hasOwner());
    }

    @Test
    void gearPromptStateRoutesThroughLegacyEntryState() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotOfferStateRuntime.reserveGearPrompt(entry, 2_000L);

        assertEquals(2_000L, AgentBotOfferStateRuntime.pendingGearPromptAt(entry));
        assertTrue(AgentBotOfferStateRuntime.hasPendingGearPromptAfter(entry, 1_999L));
        assertFalse(AgentBotOfferStateRuntime.hasPendingGearPromptAfter(entry, 2_000L));
        assertTrue(AgentBotOfferStateRuntime.isReservedGearPrompt(entry, 2_000L));
        assertFalse(AgentBotOfferStateRuntime.isReservedGearPrompt(entry, 2_001L));

        AgentBotOfferStateRuntime.clearGearPrompt(entry);

        assertEquals(0L, AgentBotOfferStateRuntime.pendingGearPromptAt(entry));
    }

    @Test
    void offerRuntimeGearPromptMethodsDelegateToStateRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotOfferStateRuntime> state = mockStatic(AgentBotOfferStateRuntime.class)) {
            state.when(() -> AgentBotOfferStateRuntime.hasPendingGearPromptAfter(entry, 1_999L)).thenReturn(true);
            state.when(() -> AgentBotOfferStateRuntime.isReservedGearPrompt(entry, 2_000L)).thenReturn(true);

            assertTrue(AgentBotOfferRuntime.hasPendingGearPromptAfter(entry, 1_999L));
            AgentBotOfferRuntime.reserveGearPrompt(entry, 2_000L);
            assertTrue(AgentBotOfferRuntime.isReservedGearPrompt(entry, 2_000L));
            AgentBotOfferRuntime.clearGearPrompt(entry);

            state.verify(() -> AgentBotOfferStateRuntime.hasPendingGearPromptAfter(entry, 1_999L));
            state.verify(() -> AgentBotOfferStateRuntime.reserveGearPrompt(entry, 2_000L));
            state.verify(() -> AgentBotOfferStateRuntime.isReservedGearPrompt(entry, 2_000L));
            state.verify(() -> AgentBotOfferStateRuntime.clearGearPrompt(entry));
        }
    }

    @Test
    void offerReplyAndSchedulerMethodsDelegateToBroadAgentRuntimes() {
        BotEntry entry = new BotEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(1800, 2200)).thenReturn(1900L);
            replies.when(() -> AgentBotReplyRuntime.queueSayWithEstimatedDelay(entry, "queued")).thenReturn(1200L);

            AgentBotOfferRuntime.replyNow(entry, "reply");
            AgentBotOfferRuntime.queueSay(entry, "say");
            AgentBotOfferRuntime.sayMapNow(null, "map");
            AgentBotOfferRuntime.sayNow(null, AgentReplyChannel.PARTY, "party");
            long queueDelay = AgentBotOfferRuntime.queueSayWithEstimatedDelay(entry, "queued");
            AgentBotOfferRuntime.afterDelay(500L, action);
            AgentBotOfferRuntime.afterRandomDelay(400, 600, action);
            long randomDelay = AgentBotOfferRuntime.randomDelayMs(1800, 2200);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "say"));
            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "map"));
            replies.verify(() -> AgentBotReplyRuntime.sayNow(null, AgentReplyChannel.PARTY, "party"));
            replies.verify(() -> AgentBotReplyRuntime.queueSayWithEstimatedDelay(entry, "queued"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(400, 600, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(1800, 2200));
            assertEquals(1200L, queueDelay);
            assertEquals(1900L, randomDelay);
        }
    }
}
