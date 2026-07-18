package server.agents.runtime.state;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentCapabilityStateRegistryTest {
    @Test
    void createsEachTypedStateOnceAndSupportsRemoval() {
        AtomicInteger creates = new AtomicInteger();
        AgentCapabilityStateKey<StringBuilder> key = new AgentCapabilityStateKey<>(
                "test.state", StringBuilder.class, () -> {
            creates.incrementAndGet();
            return new StringBuilder();
        });
        AgentCapabilityStateRegistry registry = new AgentCapabilityStateRegistry();

        assertSame(registry.require(key), registry.require(key));
        assertEquals(1, creates.get());
        assertTrue(registry.find(key).isPresent());
        assertTrue(registry.remove(key).isPresent());
        assertTrue(registry.find(key).isEmpty());
    }

    @Test
    void rejectsTwoTypesUsingTheSameIdentity() {
        AgentCapabilityStateRegistry registry = new AgentCapabilityStateRegistry();
        registry.require(new AgentCapabilityStateKey<>("duplicate", StringBuilder.class, StringBuilder::new));

        assertThrows(IllegalStateException.class, () -> registry.require(
                new AgentCapabilityStateKey<>("duplicate", StringBuffer.class, StringBuffer::new)));
    }
}
