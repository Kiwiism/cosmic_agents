package server.agents.runtime.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentActivityHostTest {
    @Test
    void evaluatesByPrecedenceAndAllowsExplicitPassThrough() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        List<String> ticks = new ArrayList<>();
        AgentActivityHost host = host(
                controller("lower", 10, AgentActivityTick.CONSUMED, ticks),
                controller("handoff", 30, AgentActivityTick.PASS, ticks),
                controller("questing", 20, AgentActivityTick.IDLE, ticks));

        assertFalse(host.tick(entry, entry.bot(), 100L));

        assertEquals(List.of("handoff", "questing"), ticks);
        AgentActivityHostState state =
                entry.capabilityStates().require(AgentActivityHostState.STATE_KEY);
        assertEquals("questing", state.controllerId());
        assertEquals(AgentActivityKind.QUESTING, state.activityKind());
        assertEquals(1L, state.transitionCount());
    }

    @Test
    void consumedOwnerStopsLowerPrecedenceControllers() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        List<String> ticks = new ArrayList<>();
        AgentActivityHost host = host(
                controller("lower", 10, AgentActivityTick.CONSUMED, ticks),
                controller("hunting", 20, AgentActivityTick.CONSUMED, ticks));

        assertTrue(host.tick(entry, entry.bot(), 100L));

        assertEquals(List.of("hunting"), ticks);
        assertEquals("hunting", entry.capabilityStates()
                .require(AgentActivityHostState.STATE_KEY).controllerId());
    }

    @Test
    void clearsOwnershipWhenNoControllerRemainsActive() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        assertFalse(host(controller("questing", 20, AgentActivityTick.IDLE,
                new ArrayList<>())).tick(entry, entry.bot(), 100L));
        assertFalse(new AgentActivityHost(new AgentActivityControllerRegistry(List.of()))
                .tick(entry, entry.bot(), 200L));

        AgentActivityHostState state =
                entry.capabilityStates().require(AgentActivityHostState.STATE_KEY);
        assertEquals(null, state.controllerId());
        assertEquals("questing", state.previousControllerId());
        assertEquals(2L, state.transitionCount());
    }

    @Test
    void registryRejectsDuplicatePrimaryKinds() {
        List<String> ticks = new ArrayList<>();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentActivityControllerRegistry(List.of(
                        controller("questing", 20, AgentActivityTick.IDLE, ticks),
                        controller("questing-copy", 10, AgentActivityTick.IDLE, ticks))));
    }

    private static AgentActivityHost host(AgentActivityController... controllers) {
        return new AgentActivityHost(new AgentActivityControllerRegistry(List.of(controllers)));
    }

    private static AgentActivityController controller(
            String id, int precedence, AgentActivityTick outcome, List<String> ticks) {
        return new AgentActivityController() {
            @Override public String id() { return id; }
            @Override public int precedence() { return precedence; }
            @Override public AgentActivityRole role() {
                return "hunting".equals(id) || id.startsWith("questing")
                        ? AgentActivityRole.PRIMARY : AgentActivityRole.SUPPORT;
            }
            @Override public AgentActivityKind activityKind() {
                return "hunting".equals(id) ? AgentActivityKind.HUNTING
                        : id.startsWith("questing") ? AgentActivityKind.QUESTING : null;
            }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return true; }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                ticks.add(id);
                return outcome;
            }
        };
    }
}
