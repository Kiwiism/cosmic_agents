package server.agents.runtime.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentForegroundActivityCoordinatorTest {
    @Test
    void replacesExclusiveOwnersButPreservesCoordinators() {
        AtomicInteger exclusiveStops = new AtomicInteger();
        AtomicInteger coordinatorStops = new AtomicInteger();
        AgentForegroundActivity target = activity("plan", true, new AtomicInteger());
        AgentForegroundActivity current = activity(
                "town-life", true, exclusiveStops);
        AgentForegroundActivity handoff = activity(
                "handoff", false, coordinatorStops);
        AgentForegroundActivityCoordinator coordinator =
                new AgentForegroundActivityCoordinator(
                        new AgentForegroundActivityRegistry(
                                List.of(handoff, current, target)));
        AgentRuntimeEntry entry =
                new AgentRuntimeEntry(mock(Character.class), null, null);

        boolean prepared = coordinator.prepareExclusive(
                "plan", entry, entry.bot(), "test replacement", 100L);

        assertTrue(prepared);
        assertEquals(1, exclusiveStops.get());
        assertEquals(0, coordinatorStops.get());
    }

    @Test
    void waitsForAGracefullyDrainingExclusiveOwner() {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger forcedStops = new AtomicInteger();
        AgentForegroundActivity draining = new AgentForegroundActivity() {
            @Override public String id() { return "town-life"; }
            @Override public int priority() { return 2; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return true; }
            @Override public AgentForegroundActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentForegroundActivityTick.CONSUMED;
            }
            @Override public boolean requestDeactivate(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                requests.incrementAndGet();
                return false;
            }
            @Override public void deactivate(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                forcedStops.incrementAndGet();
            }
        };
        AgentForegroundActivity target = activity("plan", true, new AtomicInteger());
        AgentForegroundActivityCoordinator coordinator = new AgentForegroundActivityCoordinator(
                new AgentForegroundActivityRegistry(List.of(draining, target)));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        boolean prepared = coordinator.prepareExclusive(
                "plan", entry, entry.bot(), "new objective", 100L);

        assertFalse(prepared);
        assertEquals(1, requests.get());
        assertEquals(0, forcedStops.get());
    }

    private static AgentForegroundActivity activity(
            String id, boolean exclusive, AtomicInteger stops) {
        return new AgentForegroundActivity() {
            @Override public String id() { return id; }
            @Override public int priority() { return 1; }
            @Override public boolean active(
                    AgentRuntimeEntry entry, Character agent) { return true; }
            @Override public AgentForegroundActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentForegroundActivityTick.IDLE;
            }
            @Override public boolean exclusive() { return exclusive; }
            @Override public void deactivate(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                stops.incrementAndGet();
            }
        };
    }
}
