package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqStateTest {
    @Test
    void tracksStageOneAndStageFiveState() {
        AgentKpqState state = new AgentKpqState();

        assertEquals(AgentKpqStage1.IDLE, state.state());
        assertEquals(-1, state.couponTarget());
        assertEquals(0L, state.waitUntilMs());
        assertEquals(0, state.lastReportedCoupons());
        assertFalse(state.stage5Claimed());

        state.setState(AgentKpqStage1.SECOND_WALK);
        state.setCouponTarget(25);
        state.setWaitUntilMs(123L);
        state.setLastReportedCoupons(15);
        state.markStage5Claimed();

        assertTrue(state.stateIs(AgentKpqStage1.SECOND_WALK));
        assertTrue(state.stateAtLeast(AgentKpqStage1.FIRST_WAIT));
        assertEquals(25, state.couponTarget());
        assertEquals(123L, state.waitUntilMs());
        assertEquals(15, state.lastReportedCoupons());
        assertTrue(state.stage5Claimed());

        state.clearStage5Claimed();
        state.resetStage1(AgentKpqStage1.IDLE);

        assertEquals(AgentKpqStage1.IDLE, state.state());
        assertEquals(-1, state.couponTarget());
        assertEquals(0L, state.waitUntilMs());
        assertEquals(0, state.lastReportedCoupons());
        assertFalse(state.stage5Claimed());
    }
}
