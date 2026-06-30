package server.agents.capabilities.movement;

import client.Character;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.io.Serial;
import java.io.Serializable;

public record AgentMovementProfile(int totalSpeedStat, int totalJumpStat) implements Serializable {
    // Serialized inside cached AgentNavigationGraph instances; keep explicit so
    // cache compatibility is controlled by GRAPH_VERSION instead of compiler-generated UIDs.
    @Serial
    private static final long serialVersionUID = 1L;

    static final int BASE_TOTAL_STAT = 100;
    static final int STAT_BUCKET_SIZE = 5;
    static final int MAX_EFFECTIVE_SPEED_STAT = 200;
    static final int MAX_EFFECTIVE_JUMP_STAT = 123;
    static final AgentMovementProfile BASE = new AgentMovementProfile(BASE_TOTAL_STAT, BASE_TOTAL_STAT);

    public AgentMovementProfile {
        totalSpeedStat = bucketStat(totalSpeedStat);
        totalJumpStat = bucketStat(totalJumpStat);
        totalSpeedStat = Math.min(totalSpeedStat, MAX_EFFECTIVE_SPEED_STAT);
        totalJumpStat = Math.min(totalJumpStat, MAX_EFFECTIVE_JUMP_STAT);
    }

    public static AgentMovementProfile base() {
        return BASE;
    }

    public static AgentMovementProfile fromCharacter(Character character) {
        if (character == null) {
            return BASE;
        }
        if (hasForcedBaseMovementStats(character)) {
            return BASE;
        }
        return new AgentMovementProfile(character.getTotalMoveSpeedStat(), character.getTotalJumpStat());
    }

    private static boolean hasForcedBaseMovementStats(Character character) {
        MapleMap map = character.getMap();
        return map != null && FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit());
    }

    private static int bucketStat(int stat) {
        int clamped = Math.max(1, stat);
        if (clamped < STAT_BUCKET_SIZE) {
            return clamped;
        }
        return clamped - Math.floorMod(clamped, STAT_BUCKET_SIZE);
    }

    public double speedMultiplier() {
        return totalSpeedStat / (double) BASE_TOTAL_STAT;
    }

    public double jumpMultiplier() {
        return totalJumpStat / (double) BASE_TOTAL_STAT;
    }

    public double walkVelocityPxs() {
        return BotMovementManager.configuredWalkVelocityPxs() * speedMultiplier();
    }

    public double hForcePxs() {
        return BotPhysicsEngine.configuredHorizontalForcePxs() * speedMultiplier();
    }

    public float jumpSpeedPxs() {
        return (float) (BotPhysicsEngine.configuredJumpSpeedPxs() * jumpMultiplier());
    }

    public float ropeJumpSpeedPxs() {
        return (float) (BotPhysicsEngine.configuredRopeJumpSpeedPxs() * jumpMultiplier());
    }
}
