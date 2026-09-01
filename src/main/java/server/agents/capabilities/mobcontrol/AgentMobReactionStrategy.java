package server.agents.capabilities.mobcontrol;

import client.Character;
import server.life.Monster;
import server.integration.MobHitReactionContext;

@FunctionalInterface
public interface AgentMobReactionStrategy {
    boolean acceptedHit(Character attacker, Monster monster, int appliedDamage,
                        MobHitReactionContext reactionContext);
}
