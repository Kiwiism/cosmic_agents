package server.agents.plans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScriptRuntimeStateTest {
    @Test
    void tracksScriptProgressIntsAndWaits() {
        AgentScriptRuntimeState state = new AgentScriptRuntimeState();

        assertEquals(null, state.scriptId());
        assertEquals(0, state.stepIndex());
        assertFalse(state.stepEntered());
        assertEquals(0, state.intValue("coupons"));

        state.reset("kpq-stage-1");
        state.markStepEntered();
        state.setIntValue("coupons", 7);
        state.waitUntil(500L);

        assertEquals("kpq-stage-1", state.scriptId());
        assertTrue(state.stepEntered());
        assertEquals(7, state.intValue("coupons"));
        assertFalse(state.waitDone(499L));
        assertTrue(state.waitDone(500L));

        state.advanceStep();

        assertEquals(1, state.stepIndex());
        assertFalse(state.stepEntered());

        state.reset(null);

        assertEquals(null, state.scriptId());
        assertEquals(0, state.stepIndex());
        assertFalse(state.stepEntered());
        assertEquals(0, state.intValue("coupons"));
        assertTrue(state.waitDone(0L));
    }
}
