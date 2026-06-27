package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotOfferRuntimeTest {
    @Test
    void recommendedGearActionsReportMissingOwner() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotOfferRuntime.recommendedGearActions(entry, null, null).hasOwner());
    }

    @Test
    void offerReplyAndSchedulerMethodsDelegateToAgentRuntime() {
        BotEntry entry = new BotEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(1800, 2200)).thenReturn(1900L);
            replies.when(() -> AgentBotReplyRuntime.queueSayWithEstimatedDelay(entry, "queued")).thenReturn(1200L);

            AgentBotOfferRuntime.replyNow(entry, "reply");
            AgentBotOfferRuntime.queueSay(entry, "say");
            long queueDelay = AgentBotOfferRuntime.queueSayWithEstimatedDelay(entry, "queued");
            AgentBotOfferRuntime.afterDelay(500L, action);
            AgentBotOfferRuntime.afterRandomDelay(400, 600, action);
            long randomDelay = AgentBotOfferRuntime.randomDelayMs(1800, 2200);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "say"));
            replies.verify(() -> AgentBotReplyRuntime.queueSayWithEstimatedDelay(entry, "queued"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(400, 600, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(1800, 2200));
            assertEquals(1200L, queueDelay);
            assertEquals(1900L, randomDelay);
        }
    }
}
