package server.agents.field;

import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentCombatPostureChangedEvent;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class AgentFieldObservationStateTest {
    @Test
    void snapshotAccruesPostureMetricsAndDefensivelyCopiesTarget() {
        AgentFieldObservationState state = new AgentFieldObservationState();
        Point target = new Point(120, 40);

        state.posture(AgentCombatPostureChangedEvent.Posture.SAFE_SHOT,
                100100, target, "safe ledge", 1_000L);
        state.attack(2, 1);
        state.damage(350);
        target.x = 999;

        AgentFieldObservationState.Snapshot snapshot = state.snapshot(1_600L);

        assertEquals(600L, snapshot.postureTimeMs()
                .get(AgentCombatPostureChangedEvent.Posture.SAFE_SHOT));
        assertEquals(1L, snapshot.attacks());
        assertEquals(2L, snapshot.hitLines());
        assertEquals(1L, snapshot.missLines());
        assertEquals(350L, snapshot.damage());
        assertEquals(new Point(120, 40), snapshot.targetPosition());
        assertNotSame(snapshot.targetPosition(), snapshot.targetPosition());
    }

    @Test
    void timelineIsBoundedForLongObservationRuns() {
        AgentFieldObservationState state = new AgentFieldObservationState();
        for (int index = 0; index < 150; index++) {
            state.lifecycle("GRINDING", "transition " + index, index);
        }

        assertEquals(96, state.snapshot(200L).timeline().size());
    }
}
