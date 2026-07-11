package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatHitCounterTest {
    @Test
    void usesLargerOfAttackCountAndBulletCount() {
        StatEffect attackCountEffect = mock(StatEffect.class);
        when(attackCountEffect.getAttackCount()).thenReturn(3);
        when(attackCountEffect.getBulletCount()).thenReturn((short) 1);

        StatEffect bulletCountEffect = mock(StatEffect.class);
        when(bulletCountEffect.getAttackCount()).thenReturn(1);
        when(bulletCountEffect.getBulletCount()).thenReturn((short) 2);

        StatEffect zeroEffect = mock(StatEffect.class);

        assertEquals(3, AgentCombatHitCounter.effectiveHitCount(attackCountEffect));
        assertEquals(2, AgentCombatHitCounter.effectiveHitCount(bulletCountEffect));
        assertEquals(1, AgentCombatHitCounter.effectiveHitCount(zeroEffect));
    }

    @Test
    void appliesShadowPartnerToEveryAttackRoute() {
        Character agent = mock(Character.class);
        when(agent.getBuffEffect(BuffStat.SHADOWPARTNER)).thenReturn(mock(StatEffect.class));

        assertEquals(2, AgentCombatHitCounter.shadowPartnerHitMultiplier(agent, AgentAttackRoute.RANGED));
        assertEquals(2, AgentCombatHitCounter.shadowPartnerHitMultiplier(agent, AgentAttackRoute.CLOSE));
        assertEquals(2, AgentCombatHitCounter.shadowPartnerHitMultiplier(agent, AgentAttackRoute.MAGIC));
        assertEquals(1, AgentCombatHitCounter.shadowPartnerHitMultiplier(null, AgentAttackRoute.RANGED));
    }

    @Test
    void capsShadowPartnerAtSevenOriginalAndFourteenPacketLines() {
        Character agent = mock(Character.class);
        when(agent.getBuffEffect(BuffStat.SHADOWPARTNER)).thenReturn(mock(StatEffect.class));

        for (AgentAttackRoute route : AgentAttackRoute.values()) {
            assertEquals(14, AgentCombatHitCounter.packetSafeHitCount(agent, route, 7));
            assertEquals(14, AgentCombatHitCounter.packetSafeHitCount(agent, route, 8));
        }
        assertEquals(15, AgentCombatHitCounter.packetSafeHitCount(mock(Character.class), AgentAttackRoute.CLOSE, 20));
    }
}
