package server.agents.runtime.activity.session;

import java.util.Optional;

/** Derives the only primary owner justified by an in-flight durable handoff. */
public final class AgentRestoredHandoffOwnerResolver {
    private final AgentActivityHandoffStore store;

    public AgentRestoredHandoffOwnerResolver(AgentActivityHandoffStore store) {
        if (store == null) throw new IllegalArgumentException("handoff store is required");
        this.store = store;
    }

    public Optional<AgentActivityKind> expectedOwner(int agentId) {
        if (agentId <= 0) return Optional.empty();
        String identity = Integer.toString(agentId);
        return store.list().stream()
                .filter(handoff -> !handoff.terminal())
                .filter(handoff -> handoff.agentId().equals(identity))
                .findFirst()
                .map(handoff -> switch (handoff.phase()) {
                    case ROLLBACK_SOURCE -> handoff.sourceKind();
                    case REQUEST_SOURCE_EXIT, WAIT_SOURCE_RELEASE -> handoff.sourceKind();
                    case TRANSFER, REQUEST_TARGET_ENTRY -> handoff.targetKind();
                    case COMPLETED, ROLLED_BACK, FAILED -> throw new IllegalStateException(
                            "terminal handoff passed the in-flight filter");
                });
    }
}
