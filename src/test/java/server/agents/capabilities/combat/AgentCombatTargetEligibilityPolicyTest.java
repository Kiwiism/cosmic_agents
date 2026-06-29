package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterStats;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatTargetEligibilityPolicyTest {
    @Test
    void shouldAcceptLivingHostileMonsterWithStatsOrMissingStats() {
        Monster withoutStats = mock(Monster.class);
        when(withoutStats.isAlive()).thenReturn(true);
        when(withoutStats.getStats()).thenReturn(null);

        MonsterStats hostileStats = mock(MonsterStats.class);
        when(hostileStats.isFriendly()).thenReturn(false);
        Monster hostile = mock(Monster.class);
        when(hostile.isAlive()).thenReturn(true);
        when(hostile.getStats()).thenReturn(hostileStats);

        assertTrue(AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(withoutStats));
        assertTrue(AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(hostile));
    }

    @Test
    void shouldRejectNullDeadOrFriendlyMonster() {
        Monster dead = mock(Monster.class);
        when(dead.isAlive()).thenReturn(false);

        MonsterStats friendlyStats = mock(MonsterStats.class);
        when(friendlyStats.isFriendly()).thenReturn(true);
        Monster friendly = mock(Monster.class);
        when(friendly.isAlive()).thenReturn(true);
        when(friendly.getStats()).thenReturn(friendlyStats);

        assertFalse(AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(null));
        assertFalse(AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(dead));
        assertFalse(AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(friendly));
    }
}
