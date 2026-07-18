package server.agents.events;

@FunctionalInterface
public interface AgentEventSubscription extends AutoCloseable {
    @Override
    void close();
}
