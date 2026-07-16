package server.agents.capabilities.mobcontrol;

import client.Character;
import server.life.Monster;

public enum OffMobReactionStrategy implements AgentMobReactionStrategy {
    INSTANCE;

    @Override
    public void acceptedHit(Character attacker, Monster monster, int appliedDamage, long reactionDelayMs) {
        // Original Cosmic behavior: no Agent-generated monster reaction.
    }
}
