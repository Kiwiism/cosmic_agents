package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.capabilities.movement.AgentActionLockPhysicsService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentActionLockPhysicsServiceTest {
    @Test
    void returnsFalseWhenAttackCooldownIsNotActive() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Counters counters = new Counters();

        boolean locked = AgentActionLockPhysicsService.tickActionLocked(
                entry, ignored -> true, ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(), ignored -> counters.grounded.incrementAndGet());

        assertFalse(locked);
        counters.assertNoTicks();
    }

    @Test
    void swimsWhenLockedInAirOnSwimMap() {
        AgentRuntimeEntry entry = lockedEntry();
        AgentMovementStateRuntime.setInAir(entry, true);
        Counters counters = new Counters();

        boolean locked = AgentActionLockPhysicsService.tickActionLocked(
                entry, ignored -> true, ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(), ignored -> counters.grounded.incrementAndGet());

        assertTrue(locked);
        assertEquals(1, counters.swimming.get());
        assertEquals(0, counters.airborne.get());
        assertEquals(0, counters.grounded.get());
    }

    @Test
    void ticksAirborneWhenLockedInAirOutsideSwimMap() {
        AgentRuntimeEntry entry = lockedEntry();
        AgentMovementStateRuntime.setInAir(entry, true);
        Counters counters = new Counters();

        boolean locked = AgentActionLockPhysicsService.tickActionLocked(
                entry, ignored -> false, ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(), ignored -> counters.grounded.incrementAndGet());

        assertTrue(locked);
        assertEquals(0, counters.swimming.get());
        assertEquals(1, counters.airborne.get());
        assertEquals(0, counters.grounded.get());
    }

    @Test
    void ticksGroundedWhenLockedOnGround() {
        AgentRuntimeEntry entry = lockedEntry();
        Counters counters = new Counters();

        boolean locked = AgentActionLockPhysicsService.tickActionLocked(
                entry, ignored -> false, ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(), ignored -> counters.grounded.incrementAndGet());

        assertTrue(locked);
        assertEquals(0, counters.swimming.get());
        assertEquals(0, counters.airborne.get());
        assertEquals(1, counters.grounded.get());
    }

    @Test
    void lockedClimbingAgentStillUsesLegacyAirborneBranch() {
        AgentRuntimeEntry entry = lockedEntry();
        entry.setScriptedMovementFrame(new java.awt.Point(0, 0), 0, 0, 1, true, true);
        Counters counters = new Counters();

        boolean locked = AgentActionLockPhysicsService.tickActionLocked(
                entry, ignored -> true, ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(), ignored -> counters.grounded.incrementAndGet());

        assertTrue(locked);
        assertEquals(0, counters.swimming.get());
        assertEquals(1, counters.airborne.get());
        assertEquals(0, counters.grounded.get());
    }

    private static AgentRuntimeEntry lockedEntry() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 500);
        return entry;
    }

    private static final class Counters {
        private final AtomicInteger swimming = new AtomicInteger();
        private final AtomicInteger airborne = new AtomicInteger();
        private final AtomicInteger grounded = new AtomicInteger();

        private void assertNoTicks() {
            assertEquals(0, swimming.get());
            assertEquals(0, airborne.get());
            assertEquals(0, grounded.get());
        }
    }
}
