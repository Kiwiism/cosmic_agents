package server.agents.events;

/** Immutable fact emitted by the Agent engine after something happened. */
public interface AgentEvent {
    int agentId();

    long occurredAtMs();

    String type();

    default String dedupeKey() {
        return "";
    }
}
