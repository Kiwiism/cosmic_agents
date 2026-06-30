package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentLegacyCommandBridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentNavigationDebugOverlayTest {
    @Test
    void legacyBridgeRoutesClearToAgentOverlay() {
        Character viewer = mock(Character.class);

        try (var overlay = mockStatic(AgentNavigationDebugOverlay.class)) {
            overlay.when(() -> AgentNavigationDebugOverlay.clear(viewer)).thenReturn("Bot nav overlay cleared.");

            assertEquals("Bot nav overlay cleared.", AgentLegacyCommandBridge.clearNavigationOverlay(viewer));

            overlay.verify(() -> AgentNavigationDebugOverlay.clear(viewer));
        }
    }
}
