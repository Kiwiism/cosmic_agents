package server.agents.capabilities.navigation;

import client.Character;
import client.Skill;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.commands.AgentLegacyCommandBridge;
import server.agents.integration.SkillGateway;
import server.agents.runtime.AgentSessionLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        try (var lifecycle = mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.getBotEntries(123))
                    .thenReturn(List.of());

            assertEquals("No owned bot found. Spawn one first or use !botnav path <botName>.",
                    AgentNavigationDebugOverlay.pathLog(viewer, "", "note"));
        }
    }

    @Test
    void pathLogUsesAgentSessionNamedLookup() {
        Character viewer = mock(Character.class);
        when(viewer.getId()).thenReturn(123);

        try (var lifecycle = mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.getAgentEntry(123, "alpha"))
                    .thenReturn(null);

            assertEquals("No owned bot named 'alpha' found.",
                    AgentNavigationDebugOverlay.pathLog(viewer, "alpha", "note"));
        }
    }

    @Test
    void firstAvailableEffectUsesSkillGatewayInOrder() {
        SkillGateway skills = mock(SkillGateway.class);
        Skill secondSkill = new Skill(2);
        StatEffect effect = mock(StatEffect.class);
        secondSkill.addLevelEffect(effect);
        when(skills.getSkill(1)).thenReturn(null);
        when(skills.getSkill(2)).thenReturn(secondSkill);

        assertSame(effect, AgentNavigationDebugOverlay.firstAvailableEffect(skills, 1, 2, 3));
    }
}
