package server.agents.capabilities.mobcontrol;

import client.Character;
import server.life.Monster;
import server.integration.MobHitReactionContext;

public enum OffMobReactionStrategy implements AgentMobReactionStrategy {
    INSTANCE;

    @Override
    public void acceptedHit(Character attacker, Monster monster, int appliedDamage,
                            MobHitReactionContext reactionContext) {
        // Original Cosmic behavior: no Agent-generated monster reaction.
    }
}
