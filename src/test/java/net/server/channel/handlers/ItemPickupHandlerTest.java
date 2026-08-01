package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;
import server.maps.MapObject;
import server.maps.MapleMap;

import java.awt.Point;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemPickupHandlerTest {
    @Test
    void rejectsPickupOutsideTheAllowedDistance() {
        int objectId = 42;
        InPacket packet = packetFor(objectId);
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        MapObject item = mock(MapObject.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.getMap()).thenReturn(map);
        when(player.getPosition()).thenReturn(new Point(0, 0));
        when(map.getMapObject(objectId)).thenReturn(item);
        when(item.getPosition()).thenReturn(new Point(801, 0));

        new ItemPickupHandler().handlePacket(packet, client);

        verify(player, never()).pickupItem(item);
    }

    @Test
    void ignoresAStaleMapObjectId() {
        int objectId = 42;
        InPacket packet = packetFor(objectId);
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.getMap()).thenReturn(map);

        new ItemPickupHandler().handlePacket(packet, client);

        verify(player, never()).pickupItem(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsANearbyCurrentMapObject() {
        int objectId = 42;
        InPacket packet = packetFor(objectId);
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        MapObject item = mock(MapObject.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.getMap()).thenReturn(map);
        when(player.getPosition()).thenReturn(new Point(0, 0));
        when(map.getMapObject(objectId)).thenReturn(item);
        when(item.getPosition()).thenReturn(new Point(800, 600));

        new ItemPickupHandler().handlePacket(packet, client);

        verify(player).pickupItem(item);
    }

    private static InPacket packetFor(int objectId) {
        InPacket packet = mock(InPacket.class);
        when(packet.readInt()).thenReturn(0, objectId);
        return packet;
    }
}
