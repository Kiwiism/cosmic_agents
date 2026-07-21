package server.agents.events;

/** Immutable fact emitted by the Agent engine after something happened. */
public interface AgentEvent {
    int agentId();

    long occurredAtMs();

    String type();

    default int schemaVersion() {
        return 1;
    }

    default AgentEventContext context() {
        return AgentEventContext.from(this);
    }

    default String dedupeKey() {
        return "";
    }
}
