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
        if (route == null || agent == null) {
            return 1;
        }
        return agent.getBuffEffect(BuffStat.SHADOWPARTNER) != null ? 2 : 1;
    }

    public static int packetSafeHitCount(Character agent, AgentAttackRoute route, int originalHits) {
        int normalizedOriginal = Math.max(1, originalHits);
        if (shadowPartnerHitMultiplier(agent, route) == 1) {
            return Math.min(15, normalizedOriginal);
        }
        return Math.min(7, normalizedOriginal) * 2;
    }
}
