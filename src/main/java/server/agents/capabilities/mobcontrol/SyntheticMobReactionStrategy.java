package server.agents.capabilities.mobcontrol;

import client.Character;
import server.agents.capabilities.combat.AgentSyntheticMobReactionService;
import server.life.Monster;

public enum SyntheticMobReactionStrategy implements AgentMobReactionStrategy {
    INSTANCE;

    @Override
    public void acceptedHit(Character attacker, Monster monster, int appliedDamage, long reactionDelayMs) {
        AgentSyntheticMobReactionService.acceptedHit(attacker, monster, appliedDamage, reactionDelayMs);
    }
}
