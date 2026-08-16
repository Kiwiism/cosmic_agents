package server.agents.runtime.activity.session;

/** Durable facade around the pure two-phase handoff state machine. */
public final class AgentPersistentActivityHandoffCoordinator {
    private final AgentActivityHandoffCoordinator coordinator;
    private final AgentActivityHandoffStore store;

    public AgentPersistentActivityHandoffCoordinator(AgentActivityHandoffStore store) {
        if (store == null) {
            throw new IllegalArgumentException("handoff store is required");
        }
        this.coordinator = new AgentActivityHandoffCoordinator();
        this.store = store;
    }

    public AgentActivityHandoffCoordinator.Handoff begin(
            String handoffId,
            String callerId,
            AgentActivityKind targetKind,
            AgentActivitySourcePort source,
            AgentActivityPreflightPort targetPreflight,
            long nowMs,
            long deadlineMs) {
        AgentActivityHandoffCoordinator.Handoff handoff = coordinator.begin(
                handoffId, callerId, targetKind, source, targetPreflight, nowMs, deadlineMs);
        store.save(handoff);
        return handoff;
    }

    public AgentActivityHandoffCoordinator.Handoff advance(
            String handoffId,
            AgentActivitySourcePort source,
            AgentActivityTransferPort transfer,
            AgentActivityTargetPort target,
            long nowMs) {
        AgentActivityHandoffCoordinator.Handoff current = store.load(handoffId)
                .orElseThrow(() -> new IllegalStateException(
                        "no persisted activity handoff " + handoffId));
        AgentActivityHandoffCoordinator.Handoff next = coordinator.advance(
                current, source, transfer, target, nowMs);
        store.save(next);
        return next;
    }

    public java.util.Optional<AgentActivityHandoffCoordinator.Handoff> restore(String handoffId) {
        return store.load(handoffId);
    }

    public void acknowledgeTerminal(String handoffId) {
        AgentActivityHandoffCoordinator.Handoff handoff = store.load(handoffId)
                .orElseThrow(() -> new IllegalStateException(
                        "no persisted activity handoff " + handoffId));
        if (!handoff.terminal()) {
            throw new IllegalStateException("an in-flight handoff cannot be acknowledged");
        }
        store.delete(handoffId);
    }
}
