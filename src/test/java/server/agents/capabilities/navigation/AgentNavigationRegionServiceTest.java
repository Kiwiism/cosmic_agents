package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentNavigationRegionServiceTest {
    @Test
    void characterRegionPreservesNullCharacterGuard() {
        assertEquals(-1, AgentNavigationRegionService.resolveCharacterRegionId(null, null, null));
    }
}
