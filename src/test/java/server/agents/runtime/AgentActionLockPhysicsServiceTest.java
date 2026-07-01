package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentActionLockPhysicsServiceTest {
    @Test
    void returnsFalseWhenAttackCooldownIsNotActive() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Counters counters = new Counters();

        boolean locked = AgentActionLockPhysicsService.tickActionLocked(
                entry, ignored -> true, ignored -> counters.swimming.incrementAndGet(),
                ignored -> counters.airborne.incrementAndGet(), ignored -> counters.grounded.incrementAndGet());

        assertFalse(locked);
        counters.assertNoTicks();
    }

    @Test
    void swimsWhenLockedInAirOnSwimMap() {
        BotEntry entry = lockedEntry();
        AgentBotMovementStateRuntime.setInAir(entry, true);
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
        BotEntry entry = lockedEntry();
        AgentBotMovementStateRuntime.setInAir(entry, true);
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
        BotEntry entry = lockedEntry();
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
        BotEntry entry = lockedEntry();
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

    private static BotEntry lockedEntry() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, 500);
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
