package server.agents.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BoundedAgentEventBusTest {
    @Test
    void deliversInOrderWithinExplicitBudget() {
        BoundedAgentEventBus bus = new BoundedAgentEventBus(4);
        List<String> delivered = new ArrayList<>();
        bus.subscribe("test", event -> delivered.add(event.dedupeKey()));
        bus.publish(event("one"));
        bus.publish(event("two"));

        assertEquals(1, bus.drain(1));
        assertEquals(List.of("one"), delivered);
        assertEquals(1, bus.snapshot().queued());
        assertEquals(1, bus.drain(1));
        assertEquals(List.of("one", "two"), delivered);
    }

    @Test
    void deduplicatesAndLetsCriticalEventsEvictAmbientWork() {
        BoundedAgentEventBus bus = new BoundedAgentEventBus(2);
        assertTrue(bus.publish(event("one"), AgentEventPriority.AMBIENT));
        assertFalse(bus.publish(event("one"), AgentEventPriority.NORMAL));
        assertTrue(bus.publish(event("two"), AgentEventPriority.NORMAL));
        assertTrue(bus.publish(event("critical"), AgentEventPriority.CRITICAL));

        assertEquals(2, bus.snapshot().queued());
        assertEquals(2, bus.snapshot().highWaterMark());
        assertEquals(1, bus.snapshot().deduplicated());
        assertEquals(1, bus.snapshot().dropped());
    }

    @Test
    void permitsTheSameStateTransitionAfterTheEarlierEventIsConsumed() {
        BoundedAgentEventBus bus = new BoundedAgentEventBus(2);
        assertTrue(bus.publish(event("repeat")));
        assertEquals(1, bus.drain(1));

        assertTrue(bus.publish(event("repeat")));
        assertEquals(1, bus.snapshot().queued());
    }

    @Test
    void listenerFailureDoesNotBlockOtherProjection() {
        BoundedAgentEventBus bus = new BoundedAgentEventBus(2);
        List<String> delivered = new ArrayList<>();
        bus.subscribe("test", ignored -> { throw new IllegalStateException("projection failed"); });
        bus.subscribe("test", event -> delivered.add(event.dedupeKey()));
        bus.publish(event("safe"));

        assertEquals(1, bus.drain(1));
        assertEquals(List.of("safe"), delivered);
        assertEquals(2, bus.snapshot().listenerInvocations());
        assertEquals(1, bus.snapshot().listenerFailures());
        assertTrue(bus.snapshot().listenerMaxDurationNs() >= 0L);
        assertTrue(bus.snapshot().queueLatencyMaxNs() >= 0L);
    }

    @Test
    void envelopesPayloadWithVersionedSourceAndIdentity() {
        AgentDomainEvent event = event("context");

        AgentEventEnvelope envelope = AgentEventEnvelope.queued(
                7L, event, AgentEventPriority.NORMAL, 11L);

        assertEquals("1:100:7:test", envelope.eventId());
        assertEquals(1, envelope.context().schemaVersion());
        assertEquals("test", envelope.context().sourceCapability());
        assertEquals("agent:1", envelope.context().correlationId());
        assertSame(event, envelope.event());
    }

    @Test
    void extractsObjectiveAndMapContextFromTypedEvents() {
        AgentEventEnvelope envelope = AgentEventEnvelope.queued(
                2L, new ContextEvent(4, 200L, 100000000, "objective-7"),
                AgentEventPriority.IMPORTANT, 12L);

        assertEquals("progression", envelope.context().sourceCapability());
        assertEquals("objective-7", envelope.context().objectiveId());
        assertEquals("objective-7", envelope.context().correlationId());
        assertEquals(100000000, envelope.context().mapId());
    }

    private static AgentDomainEvent event(String key) {
        return new AgentDomainEvent(1, 100L, "test", key, Map.of());
    }

    private record ContextEvent(
            int agentId,
            long occurredAtMs,
            int mapId,
            String objectiveId) implements AgentContextualEvent {
        @Override
        public String type() {
            return "progression.test";
        }
    }
}
