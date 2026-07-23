package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLithHarborArrivalRouteRuntimeTest {
    @Test
    void selectsHiddenPortalBasedOnCurrentShipSection() {
        assertEquals(31, portalAt(new Point(4_188, -223)));
        assertEquals(20, portalAt(new Point(5_180, -319)));
        assertEquals(20, portalAt(new Point(4_300, 527)));
        assertEquals(30, portalAt(new Point(-572, 191)));
        assertNull(portalAt(new Point(2_407, -134)));
        assertNull(portalAt(new Point(2_894, 423)));
    }

    private static Integer portalAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(position);
        return AgentLithHarborArrivalRouteRuntime.nextPortalId(agent);
    }
}
