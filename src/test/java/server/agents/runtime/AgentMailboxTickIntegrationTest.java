package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.agents.events.AgentDomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMailboxTickIntegrationTest {
    @Test
    void guardedTickDrainsMailboxBeforeGameplayCore() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> order = new ArrayList<>();
        AgentMailboxRuntime.submit(entry, ignored -> {
            order.add("mailbox");
            return null;
        });

        AgentTickOrchestrator.runGuardedTick(
                entry,
                1,
                2,
                (ignored, leaderId, agentId) -> order.add("tick"),
                (ignored, leaderId, agentId, failure) -> order.add("failure"));

        assertEquals(List.of("mailbox", "tick"), order);
    }

    @Test
    void guardedTickDispatchesEventsAfterGameplayCore() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> order = new ArrayList<>();
        AgentSessionEventRuntime.bus(entry).subscribe("test.completed-frame",
                ignored -> order.add("event"));

        AgentTickOrchestrator.runGuardedTick(
                entry,
                1,
                2,
                (ignored, leaderId, agentId) -> {
                    order.add("tick");
                    AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(
                            2, 10L, "test.completed-frame", "once", Map.of()));
                },
                (ignored, leaderId, agentId, failure) -> order.add("failure"));

        assertEquals(List.of("tick", "event"), order);
    }
}
