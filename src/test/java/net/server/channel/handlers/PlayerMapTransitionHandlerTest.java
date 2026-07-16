package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapObject;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerMapTransitionHandlerTest {
    @Test
    void acknowledgedFieldUsesOneControlHandoffWithoutRespawningMonsters() {
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);

        when(client.getPlayer()).thenReturn(character);
        when(character.getClient()).thenReturn(client);
        when(character.getMap()).thenReturn(map);
        when(character.isChangingMaps()).thenReturn(true);
        when(character.getBuffSource(org.mockito.ArgumentMatchers.any())).thenReturn(-1);
        when(monster.getSpawnEffect()).thenReturn(0);
        when(monster.getHp()).thenReturn(5);
        when(monster.getMaxHp()).thenReturn(8);
        when(monster.isAlive()).thenReturn(true);
        when(monster.getObjectId()).thenReturn(1_000_000_001);
        when(map.getMonsters()).thenReturn(List.<MapObject>of(monster));
        when(map.getMonsterByOid(monster.getObjectId())).thenReturn(monster);

        new PlayerMapTransitionHandler().handlePacket(mock(InPacket.class), client);

        var order = inOrder(map, character, monster);
        order.verify(map).beginMobPhysicsObserverWarmup();
        order.verify(character).setMapTransitionComplete();
        order.verify(monster).aggroSwitchController(character, false);
        verify(monster, never()).aggroRemoveController();
        verify(monster, never()).sendDestroyData(client);
        verify(monster, never()).sendSpawnData(client);
    }

    @Test
    void duplicateAcknowledgementDoesNotChurnMonsterControl() {
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(client.getPlayer()).thenReturn(character);
        when(character.getClient()).thenReturn(client);
        when(character.getMap()).thenReturn(map);
        when(character.isChangingMaps()).thenReturn(false);

        new PlayerMapTransitionHandler().handlePacket(mock(InPacket.class), client);

        verify(map, never()).beginMobPhysicsObserverWarmup();
        verify(character, never()).setMapTransitionComplete();
    }
}
