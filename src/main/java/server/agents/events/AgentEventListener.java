package server.agents.events;

public interface AgentEventListener<E extends AgentEvent> {
    void onAgentEvent(E event);
}

