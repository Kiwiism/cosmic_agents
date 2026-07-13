package server.agents.capabilities.trade;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentOfferRuntimeTest {
    @Test
    void recommendedGearActionsReportMissingOwner() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentOfferRuntime.recommendedGearActions(entry, null, null).hasOwner());
    }

    @Test
    void gearPromptStateRoutesThroughLegacyEntryState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentOfferStateRuntime.reserveGearPrompt(entry, 2_000L);

        assertEquals(2_000L, AgentOfferStateRuntime.pendingGearPromptAt(entry));
        assertTrue(AgentOfferStateRuntime.hasPendingGearPromptAfter(entry, 1_999L));
        assertFalse(AgentOfferStateRuntime.hasPendingGearPromptAfter(entry, 2_000L));
        assertTrue(AgentOfferStateRuntime.isReservedGearPrompt(entry, 2_000L));
        assertFalse(AgentOfferStateRuntime.isReservedGearPrompt(entry, 2_001L));

        AgentOfferStateRuntime.clearGearPrompt(entry);

        assertEquals(0L, AgentOfferStateRuntime.pendingGearPromptAt(entry));
    }

    @Test
    void offerRuntimeGearPromptMethodsDelegateToStateRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentOfferStateRuntime> state = mockStatic(AgentOfferStateRuntime.class)) {
            state.when(() -> AgentOfferStateRuntime.hasPendingGearPromptAfter(entry, 1_999L)).thenReturn(true);
            state.when(() -> AgentOfferStateRuntime.isReservedGearPrompt(entry, 2_000L)).thenReturn(true);

            assertTrue(AgentOfferRuntime.hasPendingGearPromptAfter(entry, 1_999L));
            AgentOfferRuntime.reserveGearPrompt(entry, 2_000L);
            assertTrue(AgentOfferRuntime.isReservedGearPrompt(entry, 2_000L));
            AgentOfferRuntime.clearGearPrompt(entry);

            state.verify(() -> AgentOfferStateRuntime.hasPendingGearPromptAfter(entry, 1_999L));
            state.verify(() -> AgentOfferStateRuntime.reserveGearPrompt(entry, 2_000L));
            state.verify(() -> AgentOfferStateRuntime.isReservedGearPrompt(entry, 2_000L));
            state.verify(() -> AgentOfferStateRuntime.clearGearPrompt(entry));
        }
    }

    @Test
    void offerReplyAndSchedulerMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(1800, 2200)).thenReturn(1900L);
            replies.when(() -> AgentReplyRuntime.queueSayWithEstimatedDelay(entry, "queued")).thenReturn(1200L);

            AgentOfferRuntime.replyNow(entry, "reply");
            AgentOfferRuntime.queueSay(entry, "say");
            AgentOfferRuntime.sayMapNow(null, "map");
            AgentOfferRuntime.sayNow(null, AgentReplyChannel.PARTY, "party");
            long queueDelay = AgentOfferRuntime.queueSayWithEstimatedDelay(entry, "queued");
            AgentOfferRuntime.afterDelay(entry, 500L, action);
            AgentOfferRuntime.afterRandomDelay(entry, 400, 600, action);
            long randomDelay = AgentOfferRuntime.randomDelayMs(1800, 2200);

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentReplyRuntime.queueSay(entry, "say"));
            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "map"));
            replies.verify(() -> AgentReplyRuntime.sayNow(null, AgentReplyChannel.PARTY, "party"));
            replies.verify(() -> AgentReplyRuntime.queueSayWithEstimatedDelay(entry, "queued"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(entry, 500L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(entry, 400, 600, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(1800, 2200));
            assertEquals(1200L, queueDelay);
            assertEquals(1900L, randomDelay);
        }
    }
}
