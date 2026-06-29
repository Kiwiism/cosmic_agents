package server.agents.capabilities.combat;

import server.life.Monster;

public final class AgentCombatTargetEligibilityPolicy {
    private AgentCombatTargetEligibilityPolicy() {
    }

    public static boolean isHostileLivingMonster(Monster monster) {
        return monster != null
                && monster.isAlive()
                && (monster.getStats() == null || !monster.getStats().isFriendly());
    }
}
