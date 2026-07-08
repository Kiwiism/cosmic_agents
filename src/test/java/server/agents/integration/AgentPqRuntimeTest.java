package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentPqRuntime;
import server.agents.integration.AgentReplyRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AgentPqRuntimeTest {
    @Test
    void pqDialogueDelegatesToAgentReplyRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentPqRuntime.queueSay(entry, "Here's your pass!");

            replies.verify(() -> AgentReplyRuntime.queueSay(entry, "Here's your pass!"));
        }
    }

    @Test
    void resetsKpqStage5ClaimedState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPqRuntime.markKpqStage5Claimed(entry);

        assertTrue(AgentPqRuntime.kpqStage5Claimed(entry));

        AgentPqRuntime.resetKpqStage5Claimed(entry);

        assertFalse(AgentPqRuntime.kpqStage5Claimed(entry));

        AgentPqRuntime.markKpqStage5Claimed(entry);

        assertTrue(AgentPqRuntime.kpqStage5Claimed(entry));
    }

    @Test
    void readsKpqCouponTargetThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPqRuntime.setKpqCouponTarget(entry, 25);

        assertEquals(25, AgentPqRuntime.kpqCouponTarget(entry));
    }

    @Test
    void readsKpqStageStateThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPqRuntime.setKpqStageState(entry, 2);

        assertEquals(2, AgentPqRuntime.kpqStageState(entry));
    }

    @Test
    void adaptsKpqStageOneStateThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentPqRuntime.setKpqStageState(entry, 4);
        assertEquals(4, AgentPqRuntime.kpqStageState(entry));
        assertTrue(AgentPqRuntime.kpqStageStateIs(entry, 4));
        assertTrue(AgentPqRuntime.kpqStageStateAtLeast(entry, 3));

        AgentPqRuntime.setKpqCouponTarget(entry, 25);
        AgentPqRuntime.setKpqLastReportedCoupons(entry, 15);
        assertEquals(25, AgentPqRuntime.kpqCouponTarget(entry));
        assertEquals(15, AgentPqRuntime.kpqLastReportedCoupons(entry));

        AgentPqRuntime.resetKpqStage1(entry, 0);
        assertEquals(0, AgentPqRuntime.kpqStageState(entry));
        assertEquals(-1, AgentPqRuntime.kpqCouponTarget(entry));
        assertEquals(0, AgentPqRuntime.kpqLastReportedCoupons(entry));
    }
}
