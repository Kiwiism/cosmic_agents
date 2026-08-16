package server.agents.runtime.field;

import org.junit.jupiter.api.Test;
import server.agents.field.AgentFieldIntent;
import server.agents.field.AgentFieldObservationState;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldActivityStateTest {
    @Test
    void lifecycleRetainsCallerIdentityAndClearsTransientRestState() {
        AgentFieldActivityState state = new AgentFieldActivityState();
        AgentFieldSessionHandle handle = new AgentFieldSessionHandle(
                "session", "request", "plan", 10, 100000000, 1_000L);
        AgentFieldVisitRequest visit = new AgentFieldVisitRequest(
                100000000, AgentFieldIntent.freeGrind("grind"), true, 6, true,
                AgentFieldObservationState.NarrationLevel.VERBOSE);

        state.start(handle, visit);
        state.rest(new Point(30, 40), 5_000L, "observe idle transition");
        assertEquals(AgentFieldActivityState.Phase.RESTING, state.snapshot().phase());
        state.completeRest();
        assertEquals(AgentFieldActivityState.Phase.GRINDING, state.snapshot().phase());
        state.suspend();
        assertEquals(AgentFieldActivityState.Phase.SUSPENDED, state.snapshot().phase());
        state.resume();
        state.drain("duration elapsed", 8_000L);

        AgentFieldActivityState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentFieldActivityState.Phase.DRAINING, snapshot.phase());
        assertEquals("plan", snapshot.handle().callerId());
        assertEquals("duration elapsed", snapshot.exitReason());
        assertFalse(snapshot.restTarget() != null);
        state.clear();
        assertFalse(state.active());
    }

    @Test
    void visitLeaseRequestsOnlyOneExit() {
        AgentFieldVisitLeaseState lease = new AgentFieldVisitLeaseState();
        lease.start(new AgentFieldSessionHandle(
                "session", "request", "plan", 10, 100000000, 1_000L),
                9_000L, 3_000L, "elapsed");

        assertTrue(lease.active());
        assertFalse(lease.exitRequested());
        lease.markExitRequested();
        assertTrue(lease.exitRequested());
        lease.clear();
        assertFalse(lease.active());
    }
}
