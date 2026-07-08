package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentLegacyCommandBridge;
import server.agents.integration.AgentSessionLifecycleSideEffects;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

    @Test
    void pathLogUsesAgentSessionEntriesForBlankTarget() {
        Character viewer = mock(Character.class);
        when(viewer.getId()).thenReturn(123);

        try (var lifecycle = mockStatic(AgentSessionLifecycleSideEffects.class)) {
            lifecycle.when(() -> AgentSessionLifecycleSideEffects.getBotEntries(123))
                    .thenReturn(List.of());

            assertEquals("No owned bot found. Spawn one first or use !botnav path <botName>.",
                    AgentNavigationDebugOverlay.pathLog(viewer, "", "note"));
        }
    }

    @Test
    void pathLogUsesAgentSessionNamedLookup() {
        Character viewer = mock(Character.class);
        when(viewer.getId()).thenReturn(123);

        try (var lifecycle = mockStatic(AgentSessionLifecycleSideEffects.class)) {
            lifecycle.when(() -> AgentSessionLifecycleSideEffects.getAgentEntry(123, "alpha"))
                    .thenReturn(null);

            assertEquals("No owned bot named 'alpha' found.",
                    AgentNavigationDebugOverlay.pathLog(viewer, "alpha", "note"));
        }
    }
}
