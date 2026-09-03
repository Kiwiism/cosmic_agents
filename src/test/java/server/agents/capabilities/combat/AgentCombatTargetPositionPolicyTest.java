package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatTargetPositionPolicyTest {
    @Test
    void detectsVisibleBodiesDetachedFromTheirPublishedOrigin() {
        Point sharedOrigin = new Point(412, 258);

        assertTrue(AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                sharedOrigin, new Rectangle(47, 119, 247, 119)));
        assertTrue(AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                sharedOrigin, new Rectangle(629, 57, 187, 171)));
    }

    @Test
    void leavesOrdinaryOriginAnchoredBodiesOnGenericTargetPosition() {
        assertFalse(AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                new Point(120, 200), new Rectangle(90, 120, 60, 80)));
        assertTrue(AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                new Point(150, 200), new Rectangle(90, 120, 60, 80)));
        assertFalse(AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                null, new Rectangle(90, 120, 60, 80)));
        assertFalse(AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                new Point(120, 200), null));
    }
}
