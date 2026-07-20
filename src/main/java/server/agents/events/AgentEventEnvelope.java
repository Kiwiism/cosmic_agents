package server.agents.events;

record AgentEventEnvelope(long sequence,
                          AgentEvent event,
                          AgentEventPriority priority,
                          long enqueuedAtNanos) {
}
