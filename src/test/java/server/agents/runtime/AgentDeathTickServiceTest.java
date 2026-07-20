package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentDeathStateRuntime;
import server.agents.capabilities.combat.AgentDeathTickService;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentDeathTickServiceTest {
    @Test
    void returnsFalseWhenAgentIsAliveAndDoesNotNeedDeadState() {
        AgentRuntimeEntry entry = entry();
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_000L);

        assertFalse(consumed);
        counters.assertCounts(0, 0);
    }

    @Test
    void entersDeadStateAndConsumesTick() {
        AgentRuntimeEntry entry = entry();
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), trueCondition(), counters::enterDead, counters::respawn, 1_000L);

        assertTrue(consumed);
        counters.assertCounts(1, 0);
        assertTrue(AgentDeathStateRuntime.isDead(entry));
    }

    @Test
    void consumesTickWhileWaitingForRespawn() {
        AgentRuntimeEntry entry = entry();
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_100L);

        assertTrue(consumed);
        counters.assertCounts(0, 0);
    }

    @Test
    void runsRespawnWhenDeadWindowIsDue() {
        AgentRuntimeEntry entry = entry();
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_500L);

        assertTrue(consumed);
        counters.assertCounts(0, 1);
    }

    private static java.util.function.BooleanSupplier falseCondition() {
        return () -> false;
    }

    private static java.util.function.BooleanSupplier trueCondition() {
        return () -> true;
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }

    private static Character agent(AgentRuntimeEntry entry) {
        return entry.bot();
    }

    private static final class Counters {
        private final AtomicInteger deadEntries = new AtomicInteger();
        private final AtomicInteger respawns = new AtomicInteger();

        private void enterDead(AgentRuntimeEntry entry, Character agent) {
            deadEntries.incrementAndGet();
            AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        }

        private void respawn() {
            respawns.incrementAndGet();
        }

        private void assertCounts(int expectedDeadEntries, int expectedRespawns) {
            assertEquals(expectedDeadEntries, deadEntries.get());
            assertEquals(expectedRespawns, respawns.get());
        }
    }
}
