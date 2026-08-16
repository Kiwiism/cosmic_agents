package server.agents.runtime.activity.session;

import java.util.Optional;

/** Durable state port for an in-flight world activity handoff. */
public interface AgentActivityHandoffStore {
    void save(AgentActivityHandoffCoordinator.Handoff handoff);

    Optional<AgentActivityHandoffCoordinator.Handoff> load(String handoffId);

    void delete(String handoffId);
}
