package server.agents.capabilities.combat;

import client.Character;

/**
 * Authoritative, transport-neutral result of one accepted monster damage application.
 * It is created only after the server has changed monster HP.
 */
public record AcceptedMobHitResult(
        Character logicalTarget,
        boolean agentTarget,
        int appliedDamage,
        int maxAcceptedDamageLine,
        boolean monsterAlive,
        boolean monsterKilled,
        boolean knockbackEligible,
        int hitDirection,
        long reactionDelayMs,
        boolean observed,
        String reaction) {
}
