package server.agents.population;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationPolicyTest {
    @Test
    void curveNeverTargetsOutsideManagedRoster() {
        AgentPopulationCurve curve = new AgentPopulationCurve();

        assertEquals(0, curve.target(10, 0.0));
        assertEquals(5, curve.target(10, 0.5));
        assertEquals(10, curve.target(10, 1.0));
        assertEquals(10, curve.target(10, 50.0));
    }

    @Test
    void multiplierRejectsInvalidOrUnboundedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentPopulationPolicy.requireMultiplier(-0.1));
        assertThrows(IllegalArgumentException.class,
                () -> AgentPopulationPolicy.requireMultiplier(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> AgentPopulationPolicy.requireMultiplier(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> AgentPopulationPolicy.requireMultiplier(101.0));
    }
}
