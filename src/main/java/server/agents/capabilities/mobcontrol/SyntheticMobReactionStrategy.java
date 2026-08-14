package server.agents.capabilities.mobcontrol;

import client.Character;
import server.agents.capabilities.combat.AgentSyntheticMobReactionService;
import server.life.Monster;
import server.integration.MobHitReactionContext;

public enum SyntheticMobReactionStrategy implements AgentMobReactionStrategy {
    INSTANCE;

    @Override
    public void acceptedHit(Character attacker, Monster monster, int appliedDamage,
                            MobHitReactionContext reactionContext) {
        AgentSyntheticMobReactionService.acceptedHit(attacker, monster, appliedDamage,
                reactionContext == null ? 0L : reactionContext.delayMs());
    }
}
