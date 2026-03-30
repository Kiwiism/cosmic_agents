package server.bots;

import client.BuffStat;
import client.Character;
import client.Job;
import client.inventory.InventoryType;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotCombatManagerTest {
    @Test
    void shouldMatchOpenStoryGroundMobKnockbackWhenHitFromRight() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(140, 200), 9300000);
        BotEntry entry = new BotEntry(bot, null, null);

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertTrue(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(-Math.round(1.5f * BotMovementManager.cfg.TICK_MS / 8.0f), entry.airVelX);
        assertEquals(-3.5f * BotMovementManager.cfg.TICK_MS / 8.0f, entry.velY, 1.0e-4f);
        assertDamageDirection(map, bot, 2, 0);
    }

    @Test
    void shouldOnlyRedirectHorizontalVelocityWhenMobHitOccursMidAir() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(60, 200), 9300001);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.physX = 100;
        entry.physY = 200;
        entry.velY = 12.5f;
        entry.airVelX = -4;

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertTrue(entry.inAir);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(12.5f, entry.velY, 1.0e-4f);
        assertEquals(Math.round(1.5f * BotMovementManager.cfg.TICK_MS / 8.0f), entry.airVelX);
        assertDamageDirection(map, bot, 2, 1);
    }

    @Test
    void shouldSkipMobKnockbackWhenStanceBuffIsMaxed() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, 100);
        Monster mob = mockMob(new Point(140, 200), 9300002);
        BotEntry entry = new BotEntry(bot, null, null);

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertFalse(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(0, entry.airVelX);
        assertEquals(0.0f, entry.velY, 1.0e-4f);
        assertDamageDirection(map, bot, 1, 0);
    }

    @Test
    void shouldKeepDirectionAwareDeadStanceAfterFatalMobHit() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 1, null);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.facingDir = -1;
        Monster mob = mockMob(new Point(140, 200), 9300003);

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertEquals(BotPhysicsEngine.cfg.DEAD_LEFT_STANCE, bot.getStance());
        assertTrue(entry.deadUntil > 0);
        assertFalse(entry.inAir);
        assertFalse(entry.climbing);
    }

    private static void assertDamageDirection(MapleMap map, Character bot, int expectedBroadcasts, int expectedDirection) {
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(map, times(expectedBroadcasts)).broadcastMessage(eq(bot), packets.capture(), eq(false));
        byte[] payload = packets.getAllValues().get(0).getBytes();
        assertEquals(expectedDirection, Byte.toUnsignedInt(payload[15]));
    }

    private static Character mockBot(Point startPosition, MapleMap map, int startingHp, Integer stancePercent) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger hp = new AtomicInteger(startingHp);
        AtomicInteger stance = new AtomicInteger(BotPhysicsEngine.cfg.STAND_RIGHT_STANCE);

        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getHp()).thenAnswer(invocation -> hp.get());
        doAnswer(invocation -> {
            hp.addAndGet(invocation.getArgument(0));
            return null;
        }).when(bot).addMPHP(anyInt(), anyInt());
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        when(bot.getId()).thenReturn(1);
        when(bot.getJob()).thenReturn(Job.BEGINNER);
        when(bot.getLevel()).thenReturn(200);
        when(bot.getTotalWdef()).thenReturn(0);
        when(bot.getTotalStr()).thenReturn(4);
        when(bot.getTotalDex()).thenReturn(4);
        when(bot.getTotalInt()).thenReturn(4);
        when(bot.getTotalLuk()).thenReturn(4);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(null);
        if (stancePercent != null) {
            when(bot.getBuffedValue(BuffStat.STANCE)).thenReturn(stancePercent);
        }
        return bot;
    }

    private static Monster mockMob(Point position, int id) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(position));
        when(mob.getId()).thenReturn(id);
        when(mob.getPADamage()).thenReturn(1_000);
        when(mob.getLevel()).thenReturn(1);
        when(mob.getAccuracy()).thenReturn(9_999);
        return mob;
    }
}
