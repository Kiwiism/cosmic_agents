package server.maps;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.integration.AgentPresence;
import server.integration.AgentPresenceProvider;
import server.life.Monster;
import server.life.MonsterStats;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapleMapAgentMobHitBridgeTest {
    @AfterEach
    void resetAgentPresence() {
        AgentPresence.install(null);
    }

    @Test
    void publishesActualSurvivingHpDeltaOnce() {
        MapleMap map = new MapleMap(100, 0, 1, 100, 1.0f);
        Character attacker = mock(Character.class);
        Monster monster = damageableMonster(100, 20, false);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger applied = new AtomicInteger();
        AtomicLong delay = new AtomicLong();
        AgentPresence.install(capturingProvider(calls, applied, delay));

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 125));

        verify(monster).damage(attacker, 80, false);
        assertEquals(1, calls.get());
        assertEquals(80, applied.get());
        assertEquals(125L, delay.get());
    }

    @Test
    void zeroHpDeltaDoesNotPublishReaction() {
        MapleMap map = new MapleMap(100, 0, 1, 100, 1.0f);
        Character attacker = mock(Character.class);
        Monster monster = damageableMonster(100, 100, false);
        AtomicInteger calls = new AtomicInteger();
        AgentPresence.install(capturingProvider(
                calls, new AtomicInteger(), new AtomicLong()));

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 0));

        assertEquals(0, calls.get());
    }

    @Test
    void lethalHitUsesNativeKillWithoutPublishingReaction() {
        MapleMap map = spy(new MapleMap(100, 0, 1, 100, 1.0f));
        Character attacker = mock(Character.class);
        Monster monster = damageableMonster(80, 0, true);
        AtomicInteger calls = new AtomicInteger();
        AgentPresence.install(capturingProvider(
                calls, new AtomicInteger(), new AtomicLong()));
        doNothing().when(map).killMonster(monster, attacker, true, (short) 90);

        assertTrue(map.damageMonster(attacker, monster, 80, (short) 90));

        verify(map).killMonster(monster, attacker, true, (short) 90);
        assertEquals(0, calls.get());
    }

    @Test
    void reactionProviderFailureCannotBreakAcceptedDamage() {
        MapleMap map = new MapleMap(100, 0, 1, 100, 1.0f);
        Character attacker = mock(Character.class);
        Monster monster = damageableMonster(100, 20, false);
        AgentPresence.install(new AgentPresenceProvider() {
            @Override
            public boolean isAgent(Character chr) {
                return true;
            }

            @Override
            public void mobHitAccepted(Character source, Monster target,
                                       int appliedDamage, long reactionDelayMs) {
                throw new IllegalStateException("simulated reaction failure");
            }
        });

        assertDoesNotThrow(() -> map.damageMonster(attacker, monster, 80, (short) 0));

        verify(monster).damage(attacker, 80, false);
        assertEquals(20, monster.getHp());
    }

    private static AgentPresenceProvider capturingProvider(
            AtomicInteger calls, AtomicInteger applied, AtomicLong delay) {
        return new AgentPresenceProvider() {
            @Override
            public boolean isAgent(Character chr) {
                return true;
            }

            @Override
            public void mobHitAccepted(Character attacker, Monster monster,
                                       int appliedDamage, long reactionDelayMs) {
                calls.incrementAndGet();
                applied.set(appliedDamage);
                delay.set(reactionDelayMs);
            }
        };
    }

    private static Monster damageableMonster(
            int hpBefore, int hpAfter, boolean killed) {
        MonsterStats stats = mock(MonsterStats.class);
        Monster monster = mock(Monster.class);
        AtomicInteger hp = new AtomicInteger(hpBefore);
        when(monster.getStats()).thenReturn(stats);
        when(monster.getHp()).thenAnswer(invocation -> hp.get());
        when(monster.isAlive()).thenAnswer(invocation -> hp.get() > 0);
        when(monster.damage(any(Character.class), anyInt(), eq(false)))
                .thenAnswer(invocation -> {
                    hp.set(hpAfter);
                    return killed;
                });
        return monster;
    }
}
