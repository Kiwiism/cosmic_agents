package server.agents.memory;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentSessionMemoryRepositoryTest {
    @Test
    void separatesMemoryKindsAndExpiresFacts() {
        AgentMemoryRepository memories =
                new AgentSessionMemoryRepository(new AgentRuntimeEntry(null, null, null));
        memories.remember(new AgentMemoryEntry(
                AgentMemoryKind.SOCIAL, "player:12", "helpful", 0.8,
                100L, 200L, "trade"), 100L);
        memories.remember(new AgentMemoryEntry(
                AgentMemoryKind.ECONOMIC, "item:2000000", "price=50", 0.9,
                100L, 500L, "market"), 100L);

        assertEquals("helpful",
                memories.recall(AgentMemoryKind.SOCIAL, "player:12", 150L).value());
        assertNull(memories.recall(AgentMemoryKind.SOCIAL, "player:12", 200L));
        assertEquals(1, memories.recallAll(AgentMemoryKind.ECONOMIC, 200L).size());
    }
}
