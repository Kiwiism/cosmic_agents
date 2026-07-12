package server.integration;

import client.Character;

/**
 * Result of the one authoritative Cosmic monster-damage application.
 */
public record MonsterDamageOutcome(Character attacker, int appliedDamage,
                                   int maxAcceptedDamageLine, boolean monsterAlive,
                                   boolean monsterKilled, int hitDirection,
                                   long reactionDelayMs) {
}
