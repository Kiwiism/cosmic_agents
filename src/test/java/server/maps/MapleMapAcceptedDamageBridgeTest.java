package server.maps;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.integration.MonsterAggroTargetBridge;
import server.integration.MonsterAggroTargetProvider;
import server.integration.MonsterDamageOutcome;
import server.life.LifeFactory.selfDestruction;
import server.life.Monster;
import server.life.MonsterStats;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapleMapAcceptedDamageBridgeTest {
    @AfterEach
    void resetBridge() {
        MonsterAggroTargetBridge.install(null);
    }

    @Test
    void publishesActualHpDeltaOnceWithoutApplyingDamageAgain() {
        MapleMap map = new MapleMap(100, 0, 1, 100, 1.0f);
        Character attacker = attacker(map, 0);
        MonsterStats stats = mock(MonsterStats.class);
        Monster monster = damageableMonster(map, stats, 100, 20, false, 10);
        OutcomeCapture capture = installCapture();

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 125, 50));

        verify(monster).damage(attacker, 80, false);
        assertEquals(1, capture.count.get());
        MonsterDamageOutcome outcome = capture.outcome.get();
        assertEquals(80, outcome.appliedDamage());
        assertEquals(50, outcome.maxAcceptedDamageLine());
        assertTrue(outcome.monsterAlive());
        assertFalse(outcome.monsterKilled());
        assertEquals(1, outcome.hitDirection());
        assertEquals(125L, outcome.reactionDelayMs());
    }

    @Test
    void rejectedDamageDoesNotPublishAnAcceptedOutcome() {
        MapleMap map = new MapleMap(100, 0, 1, 100, 1.0f);
        Character attacker = attacker(map, 0);
        MonsterStats stats = mock(MonsterStats.class);
        Monster monster = damageableMonster(map, stats, 100, 100, false, 10);
        OutcomeCapture capture = installCapture();

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 0, 80));

        verify(monster).damage(attacker, 80, false);
        assertEquals(0, capture.count.get());
    }

    @Test
    void lethalDamagePublishesOneFinalOutcomeAndOneKillEntry() {
        MapleMap map = spy(new MapleMap(100, 0, 1, 100, 1.0f));
        Character attacker = attacker(map, 0);
        MonsterStats stats = mock(MonsterStats.class);
        Monster monster = damageableMonster(map, stats, 80, 0, true, -10);
        OutcomeCapture capture = installCapture();
        doNothing().when(map).killMonster(monster, attacker, true, (short) 90);

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 90, 80));

        verify(monster).damage(attacker, 80, false);
        verify(map).killMonster(monster, attacker, true, (short) 90);
        assertEquals(1, capture.count.get());
        assertFalse(capture.outcome.get().monsterAlive());
        assertTrue(capture.outcome.get().monsterKilled());
    }

    @Test
    void selfDestructionThresholdIsReportedAsTheFinalKillOutcome() {
        MapleMap map = spy(new MapleMap(100, 0, 1, 100, 1.0f));
        Character attacker = attacker(map, 0);
        MonsterStats stats = mock(MonsterStats.class);
        when(stats.selfDestruction()).thenReturn(new selfDestruction((byte) 7, -1, 25));
        Monster monster = damageableMonster(map, stats, 100, 20, false, 10);
        OutcomeCapture capture = installCapture();
        doNothing().when(map).killMonster(monster, attacker, true, (short) 7);

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 90, 80));

        verify(monster).damage(attacker, 80, false);
        verify(map).killMonster(monster, attacker, true, (short) 7);
        assertEquals(1, capture.count.get());
        assertFalse(capture.outcome.get().monsterAlive());
        assertTrue(capture.outcome.get().monsterKilled());
    }

    private static Character attacker(MapleMap map, int x) {
        Character attacker = mock(Character.class);
        when(attacker.getMap()).thenReturn(map);
        when(attacker.getPosition()).thenReturn(new Point(x, 0));
        return attacker;
    }

    private static Monster damageableMonster(MapleMap map, MonsterStats stats,
                                             int hpBefore, int hpAfter,
                                             boolean killed, int x) {
        Monster monster = mock(Monster.class);
        AtomicInteger hp = new AtomicInteger(hpBefore);
        when(monster.getMap()).thenReturn(map);
        when(monster.getStats()).thenReturn(stats);
        when(monster.getPosition()).thenReturn(new Point(x, 0));
        when(monster.getHp()).thenAnswer(invocation -> hp.get());
        when(monster.isAlive()).thenAnswer(invocation -> hp.get() > 0);
        when(monster.damage(org.mockito.ArgumentMatchers.any(Character.class),
                org.mockito.ArgumentMatchers.anyInt(), eq(false))).thenAnswer(invocation -> {
            hp.set(hpAfter);
            return killed;
        });
        return monster;
    }

    private static OutcomeCapture installCapture() {
        OutcomeCapture capture = new OutcomeCapture();
        MonsterAggroTargetBridge.install(new MonsterAggroTargetProvider() {
            @Override
            public boolean onAcceptedDamage(Monster monster, Character attacker, int damage) {
                return false;
            }

            @Override
            public void onAcceptedDamage(Monster monster, MonsterDamageOutcome outcome) {
                capture.outcome.set(outcome);
                capture.count.incrementAndGet();
            }
        });
        return capture;
    }

    private static final class OutcomeCapture {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<MonsterDamageOutcome> outcome = new AtomicReference<>();
    }
}
