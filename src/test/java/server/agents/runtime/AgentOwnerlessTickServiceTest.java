package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentOwnerlessTickServiceTest {
    @Test
    void clearsFollowingAndStopsWhenGroundingConsumesTick() {
        BotEntry entry = entry();
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger idles = new AtomicInteger();
        AgentBotModeStateRuntime.setFollowing(entry, true);

        AgentOwnerlessTickService.tickOwnerless(
                entry, agent, true, (ignoredEntry, ignoredAgent) -> true,
                (ignoredEntry, ignoredAgent, runAiTick) -> moves.incrementAndGet(),
                () -> {
                    idles.incrementAndGet();
                    return false;
                });

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertEquals(0, moves.get());
        assertEquals(0, idles.get());
    }

    @Test
    void ticksStandaloneMoveWhenMoveTargetExists() {
        BotEntry entry = entry();
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger idles = new AtomicInteger();
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(1, 2), true);

        AgentOwnerlessTickService.tickOwnerless(
                entry, agent, true, (ignoredEntry, ignoredAgent) -> false,
                (ignoredEntry, ignoredAgent, runAiTick) -> {
                    if (runAiTick) {
                        moves.incrementAndGet();
                    }
                },
                () -> {
                    idles.incrementAndGet();
                    return false;
                });

        assertEquals(1, moves.get());
        assertEquals(0, idles.get());
    }

    @Test
    void idlesWhenNoMoveTargetExists() {
        BotEntry entry = entry();
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger idles = new AtomicInteger();

        AgentOwnerlessTickService.tickOwnerless(
                entry, agent, false, (ignoredEntry, ignoredAgent) -> false,
                (ignoredEntry, ignoredAgent, runAiTick) -> moves.incrementAndGet(),
                () -> {
                    idles.incrementAndGet();
                    return true;
                });

        assertEquals(0, moves.get());
        assertEquals(1, idles.get());
    }

    private static BotEntry entry() {
        return new BotEntry(mock(Character.class), mock(Character.class), null);
    }
}
