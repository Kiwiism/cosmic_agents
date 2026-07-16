package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNavigationTargetServiceVariationTest {
    @Test
    void variationOnlyAppliesToTheActiveScriptedMoveTarget() {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(51);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMapleIslandTravelRuntime.configure(entry, new AgentMapleIslandTravelSettings(
                77L, true, 1.2d, false, 0.0d, 1_000L, 0L));
        Point scriptedTarget = new Point(300, 100);

        assertNull(AgentNavigationTargetService.scriptedRouteVariation(
                entry, 1010000, 4, scriptedTarget));

        AgentMoveTargetStateRuntime.setMoveTarget(entry, scriptedTarget, false);

        assertNotNull(AgentNavigationTargetService.scriptedRouteVariation(
                entry, 1010000, 4, scriptedTarget));
        assertNull(AgentNavigationTargetService.scriptedRouteVariation(
                entry, 1010000, 4, new Point(500, 100)));
    }
}
