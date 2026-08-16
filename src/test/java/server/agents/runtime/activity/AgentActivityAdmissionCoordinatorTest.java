package server.agents.runtime.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentCommerceControlRuntime;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentActivityAdmissionCoordinatorTest {
    @Test
    void replacesPrimaryOwnersButPreservesSupportControllers() {
        AtomicInteger primaryStops = new AtomicInteger();
        AtomicInteger supportStops = new AtomicInteger();
        AgentActivityController target = controller("questing", AgentActivityRole.PRIMARY,
                AgentActivityKind.QUESTING, new AtomicInteger());
        AgentActivityController current = controller("town-life", AgentActivityRole.PRIMARY,
                AgentActivityKind.TOWN_LIFE, primaryStops);
        AgentActivityController support = controller("handoff", AgentActivityRole.SUPPORT,
                null, supportStops);
        AgentActivityAdmissionCoordinator coordinator = new AgentActivityAdmissionCoordinator(
                new AgentActivityControllerRegistry(List.of(support, current, target)));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        assertTrue(coordinator.prepare("questing", entry, entry.bot(), "test", 100L));
        assertEquals(1, primaryStops.get());
        assertEquals(0, supportStops.get());
    }

    @Test
    void waitsForAGracefullyDrainingPrimaryOwner() {
        AtomicInteger requests = new AtomicInteger();
        AgentActivityController draining = new AgentActivityController() {
            @Override public String id() { return "town-life"; }
            @Override public int precedence() { return 2; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.TOWN_LIFE; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return true; }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentActivityTick.CONSUMED;
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                requests.incrementAndGet();
                return false;
            }
        };
        AgentActivityController target = controller("questing", AgentActivityRole.PRIMARY,
                AgentActivityKind.QUESTING, new AtomicInteger());
        AgentActivityAdmissionCoordinator coordinator = new AgentActivityAdmissionCoordinator(
                new AgentActivityControllerRegistry(List.of(draining, target)));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        assertFalse(coordinator.prepare("questing", entry, entry.bot(), "objective", 100L));
        assertEquals(1, requests.get());
    }

    @Test
    void cannotBypassANonInterruptiblePrimaryOwner() {
        AgentActivityController commerce = new AgentActivityController() {
            @Override public String id() { return "commerce"; }
            @Override public int precedence() { return 2; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.COMMERCE; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return true; }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentActivityTick.CONSUMED;
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                return false;
            }
        };
        AgentActivityController target = controller("questing", AgentActivityRole.PRIMARY,
                AgentActivityKind.QUESTING, new AtomicInteger());
        AgentActivityAdmissionCoordinator coordinator = new AgentActivityAdmissionCoordinator(
                new AgentActivityControllerRegistry(List.of(commerce, target)));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        assertFalse(coordinator.prepare("questing", entry, entry.bot(), "command", 100L));
    }

    @Test
    void bootstrapRejectsQuestingWhileCommerceOwnsTheAgent() {
        Character agent = mock(Character.class);
        org.mockito.Mockito.when(agent.getId()).thenReturn(77);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCommerceControlRuntime.claim(77, "economy:test-admission");
        try {
            assertFalse(AgentActivityBootstrap.admission().prepare(
                    AgentActivityBootstrap.QUESTING_CONTROLLER_ID,
                    entry, agent, "operator command", 100L));
        } finally {
            AgentCommerceControlRuntime.release("economy:test-admission");
        }
    }

    private static AgentActivityController controller(
            String id, AgentActivityRole role, AgentActivityKind kind, AtomicInteger stops) {
        return new AgentActivityController() {
            @Override public String id() { return id; }
            @Override public int precedence() { return 1; }
            @Override public AgentActivityRole role() { return role; }
            @Override public AgentActivityKind activityKind() { return kind; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return true; }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentActivityTick.IDLE;
            }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                stops.incrementAndGet();
            }
        };
    }
}
