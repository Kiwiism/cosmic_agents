package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTraversalContractTest {
    @Test
    void resultDefensivelyCopiesItsTargetAndCarriesTypedStatus() {
        Point target = new Point(12, 34);
        AgentTraversalResult result = AgentTraversalResult.executed(target, true);
        target.x = 99;

        assertTrue(result.executed());
        assertTrue(result.consumedTick());
        assertEquals(new Point(12, 34), result.targetPosition());
        assertNotSame(result.targetPosition(), result.targetPosition());
    }

    @Test
    void rejectedResultIsDistinctFromTemporaryDeferral() {
        AgentTraversalResult rejected = AgentTraversalResult.rejected("invalid-edge");
        AgentTraversalResult deferred = AgentTraversalResult.deferred("approaching-launch");

        assertTrue(rejected.rejected());
        assertEquals(AgentTraversalResult.Status.DEFERRED, deferred.status());
    }
}
