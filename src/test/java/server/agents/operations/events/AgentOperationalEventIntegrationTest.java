package server.agents.operations.events;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.runtime.AgentEventDispatchRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOperationalEventIntegrationTest {
    @Test
    void grindTargetBoundaryPublishesSelectAndClearTransitions() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(401);
        Monster target = mock(Monster.class);
        when(target.getObjectId()).thenReturn(902);
        when(target.getId()).thenReturn(100100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);
        List<AgentEvent> received = new ArrayList<>();
        var subscription = bus.subscribe(AgentCombatTargetChangedEvent.TYPE, received::add);

        try {
            AgentGrindTargetStateRuntime.commitTarget(entry, target, 1_000L, 2_000L);
            AgentGrindTargetStateRuntime.clear(entry);

            assertEquals(2, AgentEventDispatchRuntime.drain(entry));
            AgentCombatTargetChangedEvent selected = assertInstanceOf(
                    AgentCombatTargetChangedEvent.class, received.get(0));
            AgentCombatTargetChangedEvent cleared = assertInstanceOf(
                    AgentCombatTargetChangedEvent.class, received.get(1));
            assertEquals(902, selected.targetObjectId());
            assertEquals(100100, selected.targetMobId());
            assertEquals(902, cleared.previousObjectId());
            assertEquals(0, cleared.targetObjectId());
        } finally {
            subscription.close();
            AgentSessionEventRuntime.close(entry);
        }
    }
}
