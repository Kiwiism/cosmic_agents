package server.agents.capabilities.looting;

import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentLootCleanupServiceTest {
    @Test
    void skipsDropThatIsStillPresentOnMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        MapItem drop = mock(MapItem.class);

        doReturn(map).when(agent).getMap();
        doReturn(false).when(drop).isPickedUp();
        doReturn(7).when(drop).getObjectId();
        doReturn(drop).when(map).getMapObject(7);

        AgentLootCleanupService.cleanupGhostDrop(agent, drop);

        verify(map, never()).getAllPlayers();
    }

    @Test
    void removesStaleDropFromVisibleHumanClients() {
        Character agent = mock(Character.class);
        Character viewer = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        MapItem drop = mock(MapItem.class);

        doReturn(map).when(agent).getMap();
        doReturn(true).when(drop).isPickedUp();
        doReturn(7).when(drop).getObjectId();
        doReturn(List.of(viewer)).when(map).getAllPlayers();
        doReturn(mock(Client.class)).when(viewer).getClient();
        doReturn(true).when(viewer).isMapObjectVisible(drop);

        AgentLootCleanupService.cleanupGhostDrop(agent, drop);

        verify(viewer).removeVisibleMapObject(drop);
        verify(viewer).sendPacket(any(Packet.class));
    }

    @Test
    void skipsInvisibleHumanClients() {
        Character agent = mock(Character.class);
        Character viewer = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        MapItem drop = mock(MapItem.class);

        doReturn(map).when(agent).getMap();
        doReturn(true).when(drop).isPickedUp();
        doReturn(7).when(drop).getObjectId();
        doReturn(List.of(viewer)).when(map).getAllPlayers();
        doReturn(mock(Client.class)).when(viewer).getClient();
        doReturn(false).when(viewer).isMapObjectVisible(drop);

        AgentLootCleanupService.cleanupGhostDrop(agent, drop);

        verify(viewer, never()).removeVisibleMapObject(drop);
        verify(viewer, never()).sendPacket(any(Packet.class));
    }
}
