package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

class AgentOpqInteractionPolicyTest {
    @Test
    void reactorHitRequiresSameMapGroundAndPhysicalReach() {
        Point agent = new Point(100, 100);
        assertTrue(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(190, 140)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, false, 1, new Point(100, 100)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 2, new Point(100, 100)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(191, 100)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(100, 141)));
    }

    @Test
    void portalsAndTriggerDropsAreLocalInteractions() {
        assertTrue(AgentOpqInteractionPolicy.mayEnterPortal(new Point(0, 0), new Point(45, 45)));
        assertFalse(AgentOpqInteractionPolicy.mayEnterPortal(new Point(0, 0), new Point(46, 0)));
        assertTrue(AgentOpqInteractionPolicy.mayDropTrigger(new Point(0, 0), new Point(38, 38)));
        assertFalse(AgentOpqInteractionPolicy.mayDropTrigger(new Point(0, 0), new Point(39, 0)));
    }
}
