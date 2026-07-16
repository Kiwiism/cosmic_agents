package server.life.simulation;

import server.life.MonsterStats;

/** Converts WZ-backed MonsterStats into the immutable simulation profile. */
public final class MobPhysicsProfileFactory {
    private MobPhysicsProfileFactory() {
    }

    public static MobPhysicsProfile from(MonsterStats stats) {
        if (stats == null) {
            throw new IllegalArgumentException("monster stats are required");
        }
        return new MobPhysicsProfile(
                (stats.getRawSpeed() + 100) * 0.001,
                (stats.getRawFlySpeed() + 100) * 0.0005,
                stats.getPushed(),
                stats.isPhysicsMobile(),
                stats.canJump(),
                stats.isPhysicsFlying(),
                stats.getFixedStance() != 0);
    }
}
