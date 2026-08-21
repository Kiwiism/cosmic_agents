package server.agents.runtime.activity.session;

import java.util.Optional;
import java.util.List;

/** Durable state port for an in-flight world activity handoff. */
public interface AgentActivityHandoffStore {
    void save(AgentActivityHandoffCoordinator.Handoff handoff);

    Optional<AgentActivityHandoffCoordinator.Handoff> load(String handoffId);

    /** Enumerates retained handoffs so startup can reconcile work not known by an in-memory id. */
    default List<AgentActivityHandoffCoordinator.Handoff> list() {
        return List.of();
    }

    void delete(String handoffId);
}
