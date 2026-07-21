package server.agents.events;

/** Delivery metadata around an immutable event payload. */
public record AgentEventEnvelope(
        long sequence,
        String eventId,
        AgentEventContext context,
        AgentEvent event,
        AgentEventPriority priority,
        long enqueuedAtNanos) {

    public AgentEventEnvelope {
        if (sequence <= 0 || eventId == null || eventId.isBlank() || context == null
                || event == null || priority == null || enqueuedAtNanos < 0) {
            throw new IllegalArgumentException("Valid event delivery metadata is required");
        }
        eventId = eventId.trim();
    }

    static AgentEventEnvelope queued(
            long sequence,
            AgentEvent event,
            AgentEventPriority priority,
            long enqueuedAtNanos) {
        String eventId = event.agentId() + ":" + event.occurredAtMs() + ":" + sequence + ":" + event.type();
        return new AgentEventEnvelope(sequence, eventId, event.context(), event, priority, enqueuedAtNanos);
    }
}
