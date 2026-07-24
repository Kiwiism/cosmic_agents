package server.agents.runtime.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentForegroundActivityArbiterTest {
    @Test
    void evaluatesByPriorityAndAllowsExplicitPassThrough() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        Character agent = entry.bot();
        List<String> ticks = new ArrayList<>();
        AgentForegroundActivityArbiter arbiter = arbiter(
                activity("lower", 10, AgentForegroundActivityTick.CONSUMED, ticks),
                activity("handoff", 30, AgentForegroundActivityTick.PASS, ticks),
                activity("plan", 20, AgentForegroundActivityTick.IDLE, ticks));

        assertFalse(arbiter.tick(entry, agent, 100L));

        assertEquals(List.of("handoff", "plan"), ticks);
        AgentForegroundActivityState state =
                entry.capabilityStates().require(AgentForegroundActivityState.STATE_KEY);
        assertEquals("plan", state.activityId());
        assertEquals(1L, state.transitionCount());
    }

    @Test
    void consumedOwnerStopsLowerPriorityActivities() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        List<String> ticks = new ArrayList<>();
        AgentForegroundActivityArbiter arbiter = arbiter(
                activity("lower", 10, AgentForegroundActivityTick.CONSUMED, ticks),
                activity("higher", 20, AgentForegroundActivityTick.CONSUMED, ticks));

        assertTrue(arbiter.tick(entry, entry.bot(), 100L));

        assertEquals(List.of("higher"), ticks);
        assertEquals("higher", entry.capabilityStates()
                .require(AgentForegroundActivityState.STATE_KEY).activityId());
    }

    @Test
    void clearsOwnershipWhenNoActivityRemainsActive() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentForegroundActivityArbiter selected =
                arbiter(activity("plan", 20, AgentForegroundActivityTick.IDLE, new ArrayList<>()));
        assertFalse(selected.tick(entry, entry.bot(), 100L));

        AgentForegroundActivityArbiter empty =
                new AgentForegroundActivityArbiter(new AgentForegroundActivityRegistry(List.of()));
        assertFalse(empty.tick(entry, entry.bot(), 200L));

        AgentForegroundActivityState state =
                entry.capabilityStates().require(AgentForegroundActivityState.STATE_KEY);
        assertEquals(null, state.activityId());
        assertEquals("plan", state.previousActivityId());
        assertEquals(2L, state.transitionCount());
    }

    private static AgentForegroundActivityArbiter arbiter(AgentForegroundActivity... activities) {
        return new AgentForegroundActivityArbiter(
                new AgentForegroundActivityRegistry(List.of(activities)));
    }

    private static AgentForegroundActivity activity(
            String id,
            int priority,
            AgentForegroundActivityTick outcome,
            List<String> ticks) {
        return new AgentForegroundActivity() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public boolean active(AgentRuntimeEntry entry, Character agent) {
                return true;
            }

            @Override
            public AgentForegroundActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                ticks.add(id);
                return outcome;
            }
        };
    }
}
