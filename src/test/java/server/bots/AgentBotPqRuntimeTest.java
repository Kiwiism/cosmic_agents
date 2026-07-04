package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPqReplyRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AgentBotPqRuntimeTest {
    @Test
    void pqDialogueDelegatesToPqReplyAdapter() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotPqReplyRuntime> replies = mockStatic(AgentBotPqReplyRuntime.class)) {
            AgentBotPqRuntime.queueSay(entry, "Here's your pass!");

            replies.verify(() -> AgentBotPqReplyRuntime.queueSay(entry, "Here's your pass!"));
        }
    }

    @Test
    void pqReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotPqReplyRuntime.queueSay(entry, "Here's your pass!");

            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "Here's your pass!"));
        }
    }

    @Test
    void resetsKpqStage5ClaimedState() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotPqRuntime.markKpqStage5Claimed(entry);

        assertTrue(AgentBotPqRuntime.kpqStage5Claimed(entry));

        AgentBotPqRuntime.resetKpqStage5Claimed(entry);

        assertFalse(AgentBotPqRuntime.kpqStage5Claimed(entry));

        AgentBotPqRuntime.markKpqStage5Claimed(entry);

        assertTrue(AgentBotPqRuntime.kpqStage5Claimed(entry));
    }

    @Test
    void readsKpqCouponTargetThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotPqRuntime.setKpqCouponTarget(entry, 25);

        assertEquals(25, AgentBotPqRuntime.kpqCouponTarget(entry));
    }

    @Test
    void readsKpqStageStateThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotPqRuntime.setKpqStageState(entry, 2);

        assertEquals(2, AgentBotPqRuntime.kpqStageState(entry));
    }

    @Test
    void adaptsKpqStageOneStateThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotPqRuntime.setKpqStageState(entry, 4);
        assertEquals(4, AgentBotPqRuntime.kpqStageState(entry));
        assertTrue(AgentBotPqRuntime.kpqStageStateIs(entry, 4));
        assertTrue(AgentBotPqRuntime.kpqStageStateAtLeast(entry, 3));

        AgentBotPqRuntime.setKpqCouponTarget(entry, 25);
        AgentBotPqRuntime.setKpqLastReportedCoupons(entry, 15);
        assertEquals(25, AgentBotPqRuntime.kpqCouponTarget(entry));
        assertEquals(15, AgentBotPqRuntime.kpqLastReportedCoupons(entry));

        AgentBotPqRuntime.resetKpqStage1(entry, 0);
        assertEquals(0, AgentBotPqRuntime.kpqStageState(entry));
        assertEquals(-1, AgentBotPqRuntime.kpqCouponTarget(entry));
        assertEquals(0, AgentBotPqRuntime.kpqLastReportedCoupons(entry));
    }
}
