package server.agents.runtime.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentActivityHostOwnershipTest {
    @Test
    void ambiguousRestoreConsumesTickWithoutRunningEitherOwner() {
        Fixture fixture = fixture();
        fixture.ownership.record(new AgentActivityOwnershipReconciliation(
                AgentActivityOwnershipReconciliation.Status.BLOCKED, null,
                List.of(AgentActivityKind.TOWN_LIFE, AgentActivityKind.HUNTING), "ambiguous"),
                1_000L);

        assertTrue(fixture.host.tick(fixture.entry, fixture.agent, 1_001L));
        assertEquals(0, fixture.town.ticks + fixture.hunt.ticks);
    }

    @Test
    void evidenceBackedConflictTicksOnlyLoserUntilItReleases() {
        Fixture fixture = fixture();
        fixture.town.releaseOnTick = true;
        fixture.ownership.record(new AgentActivityOwnershipReconciliation(
                AgentActivityOwnershipReconciliation.Status.DRAINING, AgentActivityKind.HUNTING,
                List.of(AgentActivityKind.TOWN_LIFE, AgentActivityKind.HUNTING), "draining"),
                1_000L);

        assertTrue(fixture.host.tick(fixture.entry, fixture.agent, 1_001L));
        assertEquals(1, fixture.town.ticks);
        assertEquals(0, fixture.hunt.ticks);
        fixture.host.tick(fixture.entry, fixture.agent, 1_002L);
        assertTrue(fixture.ownership.permitsExecution());
        assertEquals(1, fixture.hunt.ticks);
    }

    private static Fixture fixture() {
        FakeController town = new FakeController("town", 500, AgentActivityKind.TOWN_LIFE);
        FakeController hunt = new FakeController("hunt", 450, AgentActivityKind.HUNTING);
        AgentActivityHost host = new AgentActivityHost(
                new AgentActivityControllerRegistry(List.of(town, hunt)));
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        server.agents.runtime.state.AgentCapabilityStateRegistry states =
                new server.agents.runtime.state.AgentCapabilityStateRegistry();
        when(entry.capabilityStates()).thenReturn(states);
        Character agent = mock(Character.class);
        return new Fixture(host, entry, agent, town, hunt,
                states.require(AgentActivityOwnershipState.STATE_KEY));
    }

    private record Fixture(
            AgentActivityHost host,
            AgentRuntimeEntry entry,
            Character agent,
            FakeController town,
            FakeController hunt,
            AgentActivityOwnershipState ownership) { }

    private static final class FakeController implements AgentActivityController {
        private final String id;
        private final int precedence;
        private final AgentActivityKind kind;
        private boolean active = true;
        private boolean releaseOnTick;
        private int ticks;

        private FakeController(String id, int precedence, AgentActivityKind kind) {
            this.id = id;
            this.precedence = precedence;
            this.kind = kind;
        }

        @Override public String id() { return id; }
        @Override public int precedence() { return precedence; }
        @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
        @Override public AgentActivityKind activityKind() { return kind; }
        @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return active; }
        @Override public AgentActivityTick tick(
                AgentRuntimeEntry entry, Character agent, long nowMs) {
            ticks++;
            if (releaseOnTick) active = false;
            return AgentActivityTick.CONSUMED;
        }
        @Override public boolean requestStop(
                AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
            return !active;
        }
    }
}
