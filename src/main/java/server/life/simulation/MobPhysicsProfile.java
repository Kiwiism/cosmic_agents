package server.life.simulation;

import server.physics.PhysicsMode;

/** Cached immutable physics values loaded with the monster's ordinary WZ stats. */
public record MobPhysicsProfile(
        double walkingForce,
        double flyingForce,
        int pushed,
        boolean mobile,
        boolean canJump,
        boolean flying,
        boolean fixed) {

    public PhysicsMode mode() {
        if (fixed || !mobile) {
            return PhysicsMode.FIXED;
        }
        return flying ? PhysicsMode.FLYING : PhysicsMode.NORMAL;
    }
}
