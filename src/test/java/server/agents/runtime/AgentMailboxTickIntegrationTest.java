package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
}
