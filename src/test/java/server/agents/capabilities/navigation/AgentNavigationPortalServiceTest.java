package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentNavigationPortalServiceTest {
    @Test
    void doesNotResetNavigationStateWhenGatewayDoesNotEnterPortal() {
        Character agent = agent();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        MapGateway maps = mock(MapGateway.class);
        when(maps.enterPortal(agent, 3)).thenReturn(false);

        assertFalse(AgentNavigationPortalService.tryExecutePortal(entry, agent, 3, maps));
        verify(maps).enterPortal(agent, 3);
        assertEquals(0L, AgentNavigationDebugStateRuntime.portalUseCooldownUntilMs(entry));
    }

    @Test
    void clearsNavigationStateAndStartsCooldownWhenGatewayEntersPortal() {
        Character agent = agent();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new Object());
        MapGateway maps = mock(MapGateway.class);
        when(maps.enterPortal(agent, 4)).thenReturn(true);

        assertTrue(AgentNavigationPortalService.tryExecutePortal(entry, agent, 4, maps));
        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
        assertTrue(AgentNavigationDebugStateRuntime.portalUseCooldownUntilMs(entry) > System.currentTimeMillis());
    }

    private static Character agent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        return agent;
    }
}
