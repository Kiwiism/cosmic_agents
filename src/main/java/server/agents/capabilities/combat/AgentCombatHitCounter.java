package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import server.StatEffect;

public final class AgentCombatHitCounter {
    private AgentCombatHitCounter() {
    }

    public static int effectiveHitCount(StatEffect effect) {
        return Math.max(1, Math.max(effect.getAttackCount(), effect.getBulletCount()));
    }

    public static int shadowPartnerHitMultiplier(Character agent, AgentAttackRoute route) {
        if (route != AgentAttackRoute.RANGED || agent == null) {
            return 1;
        }
        return agent.getBuffEffect(BuffStat.SHADOWPARTNER) != null ? 2 : 1;
    }
}
