package server.agents.runtime.activity.session;

/** Durable facade around the pure two-phase handoff state machine. */
public final class AgentPersistentActivityHandoffCoordinator {
    private final AgentActivityHandoffCoordinator coordinator;
    private final AgentActivityHandoffStore store;
    private final AgentActivityHandoffJourneyRecorder journey;

    public AgentPersistentActivityHandoffCoordinator(AgentActivityHandoffStore store) {
        this(store, null);
    }

    public AgentPersistentActivityHandoffCoordinator(
            AgentActivityHandoffStore store,
            AgentActivityHandoffJourneyRecorder journey) {
        if (store == null) {
            throw new IllegalArgumentException("handoff store is required");
        }
        this.coordinator = new AgentActivityHandoffCoordinator();
        this.store = store;
        this.journey = journey;
    }

    public AgentActivityHandoffCoordinator.Handoff begin(
            String handoffId,
            String callerId,
            AgentActivityKind targetKind,
            AgentActivitySourcePort source,
            AgentActivityPreflightPort targetPreflight,
            long nowMs,
            long deadlineMs) {
        java.util.Optional<AgentActivityHandoffCoordinator.Handoff> existing =
                store.load(handoffId);
        if (existing.isPresent()) {
            AgentActivityHandoffCoordinator.Handoff retained = existing.orElseThrow();
            if (!retained.callerId().equals(callerId) || retained.targetKind() != targetKind) {
                throw new IllegalStateException(
                        "handoff id is already bound to a different request");
            }
            return retained;
        }
        AgentActivityHandoffCoordinator.Handoff handoff = coordinator.begin(
                handoffId, callerId, targetKind, source, targetPreflight, nowMs, deadlineMs);
        store.list().stream()
                .filter(candidate -> !candidate.terminal())
                .filter(candidate -> candidate.agentId().equals(handoff.agentId()))
                .findFirst()
                .ifPresent(candidate -> {
                    throw new IllegalStateException("Agent already has in-flight handoff "
                            + candidate.handoffId());
                });
        store.save(handoff);
        record(handoff);
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
        record(next);
        return next;
    }

    public AgentActivityHandoffCoordinator.Handoff advance(
            String handoffId,
            AgentActivitySourcePort source,
            AgentActivityTransferPort transfer,
            AgentActivityTargetPort target,
            AgentActivityRollbackPort rollback,
            long nowMs) {
        AgentActivityHandoffCoordinator.Handoff current = store.load(handoffId)
                .orElseThrow(() -> new IllegalStateException(
                        "no persisted activity handoff " + handoffId));
        AgentActivityHandoffCoordinator.Handoff next = coordinator.advance(
                current, source, transfer, target, rollback, nowMs);
        store.save(next);
        record(next);
        return next;
    }

    public java.util.Optional<AgentActivityHandoffCoordinator.Handoff> restore(String handoffId) {
        return store.load(handoffId);
    }

    public java.util.List<AgentActivityHandoffCoordinator.Handoff> restoreAll() {
        return store.list();
    }

    public AgentActivityHandoffCoordinator.Handoff reconcile(
            String handoffId,
            AgentActivitySourcePort source,
            AgentActivitySourcePort targetObserver,
            long nowMs) {
        AgentActivityHandoffCoordinator.Handoff current = store.load(handoffId)
                .orElseThrow(() -> new IllegalStateException(
                        "no persisted activity handoff " + handoffId));
        AgentActivityHandoffCoordinator.Handoff reconciled = coordinator.reconcile(
                current, source, targetObserver, nowMs);
        store.save(reconciled);
        record(reconciled);
        return reconciled;
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

    private void record(AgentActivityHandoffCoordinator.Handoff handoff) {
        if (journey == null || handoff == null) return;
        try {
            journey.record(Integer.parseInt(handoff.agentId()), handoff);
        } catch (NumberFormatException ignored) {
            // Non-Cosmic string Agent identities retain the durable handoff without a character journal.
        }
    }
}
