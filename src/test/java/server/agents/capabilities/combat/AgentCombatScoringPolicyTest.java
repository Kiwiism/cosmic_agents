package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Point;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;

class AgentCombatScoringPolicyTest {
    @Test
    void shouldCapExpectedDamageAtCurrentHp() {
        assertEquals(50.0d, AgentCombatScoringPolicy.capDamageByCurrentHp(100.0d, 50));
        assertEquals(40.0d, AgentCombatScoringPolicy.capDamageByCurrentHp(40.0d, 50));
        assertEquals(0.0d, AgentCombatScoringPolicy.capDamageByCurrentHp(100.0d, 0));
    }

    @Test
    void shouldEstimateLocalTravelCostFromWalkVelocityAndVerticalPenalty() {
        long expected = Math.round(100 * 1000.0 / AgentMovementProfile.base().walkVelocityPxs()) + 20 * 4L;

        assertEquals(expected, AgentCombatScoringPolicy.estimateLocalTravelCostMs(
                new Point(100, 100), new Point(200, 120), AgentMovementProfile.base()));
    }
}
