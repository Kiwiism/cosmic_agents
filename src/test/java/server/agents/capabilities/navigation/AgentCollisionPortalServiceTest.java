package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCollisionPortalServiceTest {
    @Test
    void firesScriptedTypeNineInsideClientTriggerBox() {
        Fixture fixture = fixture(9, "undodraco", 999_999_999, new Point(0, 0), new Point(50, -50));

        assertTrue(AgentCollisionPortalService.tick(
                fixture.entry(), fixture.agent(), fixture.maps()));
        verify(fixture.maps()).enterPortal(fixture.agent(), 12);
    }

    @Test
    void leavesScriptlessTypeNineAndScriptedTypeThreeInert() {
        Fixture bareNine = fixture(9, null, 999_999_999, new Point(0, 0), new Point(0, 0));
        Fixture scriptedThree = fixture(3, "gate", 100_000_000, new Point(0, 0), new Point(0, 0));

        assertFalse(AgentCollisionPortalService.tick(bareNine.entry(), bareNine.agent(), bareNine.maps()));
        assertFalse(AgentCollisionPortalService.tick(
                scriptedThree.entry(), scriptedThree.agent(), scriptedThree.maps()));
        verify(bareNine.maps(), never()).enterPortal(bareNine.agent(), 12);
        verify(scriptedThree.maps(), never()).enterPortal(scriptedThree.agent(), 12);
    }

    @Test
    void rejectsPositionOutsideDefaultHalfRange() {
        Fixture fixture = fixture(3, null, 100_000_000, new Point(0, 0), new Point(51, 0));

        assertFalse(AgentCollisionPortalService.tick(
                fixture.entry(), fixture.agent(), fixture.maps()));
        verify(fixture.maps(), never()).enterPortal(fixture.agent(), 12);
    }

    private static Fixture fixture(int portalType,
                                   String script,
                                   int targetMapId,
                                   Point portalPosition,
                                   Point agentPosition) {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Portal portal = mock(Portal.class);
        MapGateway maps = mock(MapGateway.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(agentPosition);
        when(map.getPortals()).thenReturn(List.of(portal));
        when(portal.getId()).thenReturn(12);
        when(portal.getType()).thenReturn(portalType);
        when(portal.getScriptName()).thenReturn(script);
        when(portal.getTargetMapId()).thenReturn(targetMapId);
        when(portal.getPortalStatus()).thenReturn(true);
        when(portal.getPosition()).thenReturn(portalPosition);
        when(maps.enterPortal(agent, 12)).thenReturn(true);
        return new Fixture(new AgentRuntimeEntry(agent, null, null), agent, maps);
    }

    private record Fixture(AgentRuntimeEntry entry, Character agent, MapGateway maps) {
    }
}
