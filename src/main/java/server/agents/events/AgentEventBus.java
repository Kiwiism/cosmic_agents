package server.agents.events;

public interface AgentEventBus {
    boolean publish(AgentEvent event);

    boolean publish(AgentEvent event, AgentEventPriority priority);

    AgentEventSubscription subscribe(String eventType, AgentEventListener<? super AgentEvent> listener);

    int drain(int budget);

    AgentEventBusSnapshot snapshot();

    void close();
}
