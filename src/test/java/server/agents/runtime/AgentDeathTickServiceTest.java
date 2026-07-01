package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentDeathTickServiceTest {
    @Test
    void returnsFalseWhenAgentIsAliveAndDoesNotNeedDeadState() {
        BotEntry entry = entry();
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_000L);

        assertFalse(consumed);
        counters.assertCounts(0, 0);
    }

    @Test
    void entersDeadStateAndConsumesTick() {
        BotEntry entry = entry();
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), trueCondition(), counters::enterDead, counters::respawn, 1_000L);

        assertTrue(consumed);
        counters.assertCounts(1, 0);
        assertTrue(AgentBotDeathStateRuntime.isDead(entry));
    }

    @Test
    void consumesTickWhileWaitingForRespawn() {
        BotEntry entry = entry();
        AgentBotDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_100L);

        assertTrue(consumed);
        counters.assertCounts(0, 0);
    }

    @Test
    void runsRespawnWhenDeadWindowIsDue() {
        BotEntry entry = entry();
        AgentBotDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
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

    private static BotEntry entry() {
        return new BotEntry(mock(Character.class), mock(Character.class), null);
    }

    private static Character agent(BotEntry entry) {
        return entry.bot();
    }

    private static final class Counters {
        private final AtomicInteger deadEntries = new AtomicInteger();
        private final AtomicInteger respawns = new AtomicInteger();

        private void enterDead(BotEntry entry, Character agent) {
            deadEntries.incrementAndGet();
            AgentBotDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
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
