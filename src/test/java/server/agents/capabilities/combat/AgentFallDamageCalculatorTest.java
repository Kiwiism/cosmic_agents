package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFallDamageCalculatorTest {
    @Test
    void returnsZeroAtOrBelowFallDamageThreshold() {
        assertEquals(0, AgentFallDamageCalculator.fallDamageFromDistance(0.0f));
        assertEquals(0, AgentFallDamageCalculator.fallDamageFromDistance(890.0f));
    }

    @Test
    void matchesCapturedClientFallDamageSamples() {
        assertEquals(8, AgentFallDamageCalculator.fallDamageFromDistance(916.0f));
        assertEquals(27, AgentFallDamageCalculator.fallDamageFromDistance(1094.0f));
        assertEquals(27, AgentFallDamageCalculator.fallDamageFromDistance(1132.0f));
        assertEquals(29, AgentFallDamageCalculator.fallDamageFromDistance(1421.0f));
        assertEquals(35, AgentFallDamageCalculator.fallDamageFromDistance(3861.0f));
    }
}
