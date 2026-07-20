package server.agents.events;

public record AgentEventBusSnapshot(
        int capacity,
        int queued,
        int subscriptions,
        long published,
        long delivered,
        long dropped,
        long deduplicated,
        int highWaterMark,
        long listenerInvocations,
        long listenerFailures,
        long listenerTotalDurationNs,
        long listenerMaxDurationNs,
        long queueLatencyTotalNs,
        long queueLatencyMaxNs,
        boolean closed) {
}
