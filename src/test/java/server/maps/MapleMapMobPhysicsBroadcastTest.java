package server.maps;

import client.BotClient;
import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.life.Monster;
import server.life.MonsterStats;

import java.lang.reflect.Field;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapleMapMobPhysicsBroadcastTest {
    @Test
    void requiresRecipientToCompleteMapTransition() {
        Character character = mock(Character.class);
        when(character.isChangingMaps()).thenReturn(true, false);

        assertFalse(MapleMap.isMapBroadcastRecipientReady(character));
        assertTrue(MapleMap.isMapBroadcastRecipientReady(character));
        assertFalse(MapleMap.isMapBroadcastRecipientReady(null));
    }

    @Test
    void mobPhysicsBroadcastSkipsHeadlessRecipients() {
        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        assertFalse(MapleMap.isMobPhysicsBroadcastRecipientReady(agent));

        Character player = mock(Character.class);
        when(player.getClient()).thenReturn(mock(Client.class));
        assertTrue(MapleMap.isMobPhysicsBroadcastRecipientReady(player));
    }

    @Test
    void observerWarmupBlocksAndCanBeDisabledLive() {
        int previous = AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS;
        MapleMap map = new MapleMap(1010100, 0, 1, 100000000, 1.0f);
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS = 2_000;
            map.beginMobPhysicsObserverWarmup();
            assertFalse(map.isMobPhysicsObserverWarmupComplete());

            AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS = 0;
            map.beginMobPhysicsObserverWarmup();
            assertTrue(map.isMobPhysicsObserverWarmupComplete());
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS = previous;
        }
    }

    @Test
    void removesClientVisibleMonsterThatDiedDuringTransition() {
        MapleMap map = new MapleMap(1010100, 0, 1, 100000000, 1.0f);
        MonsterStats stats = new MonsterStats();
        stats.setHp(10);
        Monster ghost = new Monster(120100, stats);
        ghost.setObjectId(1_000_000_123);

        Character character = mock(Character.class);
        Client client = mock(Client.class);
        when(character.getClient()).thenReturn(client);
        when(character.getVisibleMapObjects()).thenReturn(new MapObject[]{ghost});

        assertTrue(map.getMonsterByOid(ghost.getObjectId()) == null);
        assertTrue(map.reconcileTransitionMonsterVisibility(character) == 1);
        verify(client, times(2)).sendPacket(any(Packet.class));
        verify(character).removeVisibleMapObject(ghost);
    }

    @Test
    void ordinaryBroadcastSendsTheSamePacketToBothReadyNetworkClients() throws Exception {
        MapleMap map = new MapleMap(1010100, 0, 1, 100000000, 1.0f);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        when(first.getClient()).thenReturn(mock(Client.class));
        when(second.getClient()).thenReturn(mock(Client.class));
        Packet packet = mock(Packet.class);

        Field recipientsField = MapleMap.class.getDeclaredField("networkRecipients");
        recipientsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Collection<Character> recipients = (Collection<Character>) recipientsField.get(map);
        recipients.add(first);
        recipients.add(second);

        map.broadcastMessage(packet);

        verify(first).sendPacket(packet);
        verify(second).sendPacket(packet);
    }
}
