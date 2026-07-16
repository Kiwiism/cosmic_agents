package server.agents.capabilities.mobcontrol;

import client.Character;
import server.life.Monster;

@FunctionalInterface
public interface AgentMobReactionStrategy {
    void acceptedHit(Character attacker, Monster monster, int appliedDamage, long reactionDelayMs);
}
