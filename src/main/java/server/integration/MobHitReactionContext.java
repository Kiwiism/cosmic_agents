package server.integration;

import client.Character;
import server.life.Monster;

import java.awt.Point;

/** Immutable attack-time metadata used by optional mob-reaction implementations. */
public record MobHitReactionContext(long delayMs, int pushDirection, int originX,
                                    int skillId, DirectionSource directionSource) {
    private static final int FACING_LEFT_STANCE_BIT = 0x80;

    public enum DirectionSource {
        MELEE_ORIGIN,
        CAST_FACING,
        LEGACY_POSITION
    }

    public MobHitReactionContext {
        delayMs = Math.max(0L, delayMs);
        pushDirection = Integer.compare(pushDirection, 0);
        directionSource = directionSource == null
                ? DirectionSource.LEGACY_POSITION : directionSource;
    }

    public static MobHitReactionContext fromAttack(long delayMs, int skillId,
                                                    boolean ranged, boolean magic,
                                                    int stance, Point attackerPosition,
                                                    Point targetPosition) {
        int facingDirection = (stance & FACING_LEFT_STANCE_BIT) != 0 ? -1 : 1;
        int originX = attackerPosition == null ? 0 : attackerPosition.x;
        if (ranged || magic) {
            return new MobHitReactionContext(delayMs, facingDirection, originX, skillId,
                    DirectionSource.CAST_FACING);
        }
        int direction = targetPosition == null || attackerPosition == null
                ? 0 : Integer.compare(targetPosition.x, attackerPosition.x);
        if (direction == 0) direction = facingDirection;
        return new MobHitReactionContext(delayMs, direction, originX, skillId,
                DirectionSource.MELEE_ORIGIN);
    }

    public static MobHitReactionContext legacy(long delayMs, Character attacker,
                                                Monster monster) {
        Point attackerPosition = attacker == null ? null : attacker.getPosition();
        Point monsterPosition = monster == null ? null : monster.getPosition();
        int direction = attackerPosition == null || monsterPosition == null
                ? 0 : Integer.compare(monsterPosition.x, attackerPosition.x);
        if (direction == 0 && attacker != null) {
            direction = attacker.isFacingLeft() ? -1 : 1;
        }
        return new MobHitReactionContext(delayMs, direction,
                attackerPosition == null ? 0 : attackerPosition.x, 0,
                DirectionSource.LEGACY_POSITION);
    }
}
