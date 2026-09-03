package server.combat;

import client.BotClient;
import client.Character;
import client.Client;
import client.Job;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void serverOwnedHitDamagesAgentThroughAutopotAwarePath() {
        DamageFixture fixture = damageFixture(mock(BotClient.class));
        when(fixture.target().isChangingMaps()).thenReturn(true);

        int damage = ServerMobDamageService.applyOrdinaryAttack(
                fixture.attacker(), fixture.target(), 0, false);

        assertEquals(1, damage);
        verify(fixture.target()).addMPHPAndTriggerAutopot(-1, 0);
        verify(fixture.target(), never()).addMPHP(-1, 0);
    }

    @Test
    void serverOwnedHitDamagesHumanThroughNormalCharacterPath() {
        DamageFixture fixture = damageFixture(mock(Client.class));

        int damage = ServerMobDamageService.applyOrdinaryAttack(
                fixture.attacker(), fixture.target(), 0, false);

        assertEquals(1, damage);
        verify(fixture.target()).addMPHP(-1, 0);
        verify(fixture.target(), never()).addMPHPAndTriggerAutopot(-1, 0);
    }

    @Test
    void serverOwnedHitIgnoresHumanWhileTheirMapIsTransitioning() {
        DamageFixture fixture = damageFixture(mock(Client.class));
        when(fixture.target().isChangingMaps()).thenReturn(true);

        int damage = ServerMobDamageService.applyOrdinaryAttack(
                fixture.attacker(), fixture.target(), 0, false);

        assertEquals(0, damage);
        verify(fixture.target(), never()).addMPHP(-1, 0);
        verify(fixture.target(), never()).addMPHPAndTriggerAutopot(-1, 0);
    }

    private static DamageFixture damageFixture(Client client) {
        Monster attacker = mock(Monster.class);
        Character target = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(attacker.getId()).thenReturn(8_830_007);
        when(attacker.isAlive()).thenReturn(true);
        when(attacker.getMap()).thenReturn(map);
        when(attacker.getPosition()).thenReturn(new Point(0, 0));
        when(target.getClient()).thenReturn(client);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);
        when(target.getPosition()).thenReturn(new Point(20, 0));
        when(target.getJob()).thenReturn(Job.BEGINNER);
        return new DamageFixture(attacker, target);
    }

    private record DamageFixture(Monster attacker, Character target) {
    }
}
