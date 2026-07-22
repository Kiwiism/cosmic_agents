package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeMemoryTest {
    @Test
    void destinationCooldownSeparatesNormalAndFailedTargets() {
        AgentTownLifeMemory memory = new AgentTownLifeMemory();

        memory.remember(AgentTownLifeState.Activity.REST, "bench:1", 1_000L);

        assertTrue(memory.recentlyUsed(AgentTownLifeState.Activity.REST));
        assertFalse(memory.destinationAvailable("bench:1", 60_999L));
        assertTrue(memory.destinationAvailable("bench:1", 61_000L));

        memory.rememberFailure("bench:1", 70_000L);
        assertFalse(memory.destinationAvailable("bench:1", 189_999L));
        assertTrue(memory.destinationAvailable("bench:1", 190_000L));
    }
}
