package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentNavigationReliabilityModeTest {
    @Test
    void acceptsOnlyObservationAndActiveRouting() {
        assertEquals(AgentNavigationReliabilityMode.OBSERVE,
                AgentNavigationReliabilityMode.parse("observe"));
        assertEquals(AgentNavigationReliabilityMode.ACTIVE,
                AgentNavigationReliabilityMode.parse("ACTIVE"));
        assertThrows(IllegalStateException.class,
                () -> AgentNavigationReliabilityMode.parse("legacy"));
    }
}
