package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentOwnerlessTickServiceTest {
    @Test
    void clearsFollowingAndStopsWhenGroundingConsumesTick() {
        AgentRuntimeEntry entry = entry();
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger idles = new AtomicInteger();
        AgentModeStateRuntime.setFollowing(entry, true);

        AgentOwnerlessTickService.tickOwnerless(
                entry, agent, true, (ignoredEntry, ignoredAgent) -> true,
                (ignoredEntry, ignoredAgent, runAiTick) -> moves.incrementAndGet(),
                () -> {
                    idles.incrementAndGet();
                    return false;
                });

        assertFalse(AgentModeStateRuntime.following(entry));
        assertEquals(0, moves.get());
        assertEquals(0, idles.get());
    }

    @Test
    void ticksStandaloneMoveWhenMoveTargetExists() {
        AgentRuntimeEntry entry = entry();
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger idles = new AtomicInteger();
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(1, 2), true);

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
        AgentRuntimeEntry entry = entry();
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
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

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }
}
