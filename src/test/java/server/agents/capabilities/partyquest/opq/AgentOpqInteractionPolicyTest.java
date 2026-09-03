package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

class AgentOpqInteractionPolicyTest {
    @Test
    void reactorHitRequiresSameMapGroundAndPhysicalReach() {
        Point agent = new Point(100, 100);
        assertTrue(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(190, 140)));
        assertTrue(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(100, 160)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(100, 161)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, false, 1, new Point(100, 100)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 2, new Point(100, 100)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactor(1, agent, true, 1, new Point(191, 100)));
    }

    @Test
    void elevatedReactorAnchorUsesItsVerifiedGroundStrikePosition() {
        Point reactor = new Point(24, 53);
        Point standPoint = new Point(19, 143);
        assertTrue(AgentOpqInteractionPolicy.mayHitReactorFromGround(
                920010000, standPoint, true, 920010000, reactor, standPoint));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorFromGround(
                920010000, standPoint, false, 920010000, reactor, standPoint));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorFromGround(
                920010000, standPoint, true, 920010001, reactor, standPoint));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorFromGround(
                920010000, new Point(120, 143), true, 920010000, reactor, standPoint));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorFromGround(
                920010000, standPoint, true, 920010000, reactor, new Point(80, 143)));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorFromGround(
                920010000, standPoint, true, 920010000, reactor, new Point(24, 174)));
    }

    @Test
    void airborneReactorHitRequiresARealJumpPositionInsideTheAttackEnvelope() {
        Point reactor = new Point(201, -443);
        assertTrue(AgentOpqInteractionPolicy.mayHitReactorInAir(
                920010000, new Point(206, -405), false, 920010000, reactor));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorInAir(
                920010000, new Point(206, -405), true, 920010000, reactor));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorInAir(
                920010000, new Point(206, -382), false, 920010000, reactor));
        assertFalse(AgentOpqInteractionPolicy.mayHitReactorInAir(
                920010000, new Point(292, -405), false, 920010001, reactor));
    }

    @Test
    void cloudJumpLaunchRequiresANearbyAuthoredFootholdAboveTheReactor() {
        Point reactor = new Point(201, -443);
        assertTrue(AgentOpqInteractionPolicy.legalJumpLaunchPoint(
                reactor, new Point(147, -524)));
        assertFalse(AgentOpqInteractionPolicy.legalJumpLaunchPoint(
                reactor, new Point(201, -243)));
        assertFalse(AgentOpqInteractionPolicy.legalJumpLaunchPoint(
                reactor, new Point(110, -524)));
        assertFalse(AgentOpqInteractionPolicy.legalJumpLaunchPoint(
                reactor, new Point(147, -564)));
    }

    @Test
    void cloudMayUseASidePlatformInsideTheOrdinaryMeleeEnvelope() {
        Point reactor = new Point(-504, -540);
        assertTrue(AgentOpqInteractionPolicy.legalDirectGroundStrikePoint(
                reactor, new Point(-444, -502)));
        assertTrue(AgentOpqInteractionPolicy.legalDirectGroundStrikePoint(
                new Point(-54, -554), new Point(-48, -498)));
        assertFalse(AgentOpqInteractionPolicy.legalDirectGroundStrikePoint(
                new Point(-54, -554), new Point(-48, -493)));
        assertFalse(AgentOpqInteractionPolicy.legalDirectGroundStrikePoint(
                reactor, new Point(-413, -502)));
    }

    @Test
    void portalsAndTriggerDropsAreLocalInteractions() {
        assertTrue(AgentOpqInteractionPolicy.mayEnterPortal(new Point(0, 0), new Point(45, 45)));
        assertFalse(AgentOpqInteractionPolicy.mayEnterPortal(new Point(0, 0), new Point(46, 0)));
        assertTrue(AgentOpqInteractionPolicy.mayDropTrigger(new Point(0, 0), new Point(38, 38)));
        assertFalse(AgentOpqInteractionPolicy.mayDropTrigger(new Point(0, 0), new Point(39, 0)));
    }
}
