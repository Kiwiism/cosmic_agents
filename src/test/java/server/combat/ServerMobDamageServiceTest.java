package server.combat;

import client.Character;
import client.Job;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerMobDamageServiceTest {
    @Test
    void serverOwnedHitPacketIncludesTheVictimClient() {
        Monster attacker = mock(Monster.class);
        Character target = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(attacker.getId()).thenReturn(9_300_012);
        when(attacker.isAlive()).thenReturn(true);
        when(attacker.getMap()).thenReturn(map);
        when(attacker.getPosition()).thenReturn(new Point(0, 0));
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);
        when(target.getPosition()).thenReturn(new Point(20, 0));
        when(target.getJob()).thenReturn(Job.BEGINNER);

        ServerMobDamageService.applyOrdinaryAttack(attacker, target, 0, false);

        verify(map).broadcastMessage(eq(target), any(Packet.class), eq(true));
    }
}
