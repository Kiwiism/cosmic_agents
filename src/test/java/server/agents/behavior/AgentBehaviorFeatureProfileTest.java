package server.agents.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBehaviorFeatureProfileTest {
    @Test
    void parsesExplicitProfiles() {
        assertTrue(AgentBehaviorFeatureProfile.parse("standard").enabled());
        assertFalse(AgentBehaviorFeatureProfile.parse("OFF").enabled());
        assertThrows(IllegalStateException.class,
                () -> AgentBehaviorFeatureProfile.parse("custom"));
    }
}
