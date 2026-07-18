package server.agents.events;

public record AgentEventBusSnapshot(
        int capacity,
        int queued,
        int subscriptions,
        long published,
        long delivered,
        long dropped,
        long deduplicated,
        boolean closed) {
}
