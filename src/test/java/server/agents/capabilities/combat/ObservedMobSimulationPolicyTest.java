package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservedMobSimulationPolicyTest {
    @Test
    void requiresBothFeatureAndRealObserver() {
        assertTrue(ObservedMobSimulationPolicy.shouldSimulate(true, true));
        assertFalse(ObservedMobSimulationPolicy.shouldSimulate(true, false));
        assertFalse(ObservedMobSimulationPolicy.shouldSimulate(false, true));
        assertFalse(ObservedMobSimulationPolicy.shouldSimulate(false, false));
    }
}
