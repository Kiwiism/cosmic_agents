package server.life.simulation;

import org.junit.jupiter.api.Test;
import server.life.MonsterStats;
import server.physics.PhysicsMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobPhysicsProfileFactoryTest {
    @Test
    void convertsRawJourneyForcesAndCapabilities() {
        MonsterStats stats = new MonsterStats();
        stats.setRawSpeed(-20);
        stats.setRawFlySpeed(40);
        stats.setPushed(7);
        stats.setPhysicsMobile(true);
        stats.setPhysicsFlying(true);
        stats.setCanJump(false);

        MobPhysicsProfile profile = MobPhysicsProfileFactory.from(stats);

        assertEquals(0.08, profile.walkingForce(), 1.0e-12);
        assertEquals(0.07, profile.flyingForce(), 1.0e-12);
        assertEquals(7, profile.pushed());
        assertTrue(profile.mobile());
        assertTrue(profile.flying());
        assertEquals(PhysicsMode.FLYING, profile.mode());
    }

    @Test
    void immobileProfileUsesFixedPhysicsEvenWithoutNoFlipStance() {
        MonsterStats stats = new MonsterStats();
        stats.setPhysicsMobile(false);
        assertEquals(PhysicsMode.FIXED, MobPhysicsProfileFactory.from(stats).mode());
    }
}
