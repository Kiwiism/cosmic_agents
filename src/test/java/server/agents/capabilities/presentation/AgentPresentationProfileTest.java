package server.agents.capabilities.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPresentationProfileTest {
    @Test
    void parsesExplicitProfiles() {
        assertTrue(AgentPresentationProfile.parse("standard").enabled());
        assertFalse(AgentPresentationProfile.parse("OFF").enabled());
        assertThrows(IllegalStateException.class,
                () -> AgentPresentationProfile.parse("custom"));
    }
}
