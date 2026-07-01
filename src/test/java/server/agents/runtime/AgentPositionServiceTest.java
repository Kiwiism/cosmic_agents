package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPositionServiceTest {
    @Test
    void returnsFalseForMissingPoints() {
        assertFalse(AgentPositionService.isNear(null, new Point(0, 0), 8));
        assertFalse(AgentPositionService.isNear(new Point(0, 0), null, 8));
    }

    @Test
    void treatsDistanceAsInclusivePerAxis() {
        assertTrue(AgentPositionService.isNear(new Point(10, 20), new Point(18, 12), 8));
    }

    @Test
    void returnsFalseWhenEitherAxisExceedsDistance() {
        assertFalse(AgentPositionService.isNear(new Point(10, 20), new Point(19, 20), 8));
        assertFalse(AgentPositionService.isNear(new Point(10, 20), new Point(10, 29), 8));
    }
}
