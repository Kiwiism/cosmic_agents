package server.agents.events;

/** Agent event carrying execution context shared across capability boundaries. */
public interface AgentContextualEvent extends AgentEvent {
    String objectiveId();

    default int mapId() {
        return -1;
    }

    default String correlationId() {
        return objectiveId();
    }

    default String causationId() {
        return "";
    }
}
