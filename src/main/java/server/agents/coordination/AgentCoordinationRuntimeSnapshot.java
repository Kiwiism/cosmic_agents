package server.agents.coordination;

/** Process-wide bounded-router counters for operations and load tests. */
public record AgentCoordinationRuntimeSnapshot(
        long published,
        long accepted,
        long rejectedCapacity,
        long expired,
        long delivered,
        long listenerFailures,
        long receipts,
        int routes,
        int queued) {
}
