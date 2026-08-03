package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Authoritative provenance for the most recent Agent map transition. */
public final class AgentMapTransitionReceiptRuntime {
    private static final AgentCapabilityStateKey<State> STATE_KEY = new AgentCapabilityStateKey<>(
            "movement.map-transition-receipt", State.class, State::new);

    private AgentMapTransitionReceiptRuntime() {
    }

    public static void record(AgentRuntimeEntry entry,
                              int sourceMapId,
                              int sourcePortalId,
                              int destinationMapId,
                              int destinationPortalId,
                              long transitionedAtMs) {
        if (entry == null) {
            return;
        }
        State state = entry.capabilityStates().require(STATE_KEY);
        state.receipt = new Receipt(sourceMapId, sourcePortalId, destinationMapId,
                destinationPortalId, transitionedAtMs);
    }

    public static Receipt consumeForDestination(AgentRuntimeEntry entry, int destinationMapId) {
        if (entry == null) {
            return null;
        }
        State state = entry.capabilityStates().require(STATE_KEY);
        Receipt receipt = state.receipt;
        if (receipt == null || receipt.destinationMapId() != destinationMapId) {
            return null;
        }
        state.receipt = null;
        return receipt;
    }

    public record Receipt(int sourceMapId,
                          int sourcePortalId,
                          int destinationMapId,
                          int destinationPortalId,
                          long transitionedAtMs) {
    }

    private static final class State {
        private Receipt receipt;
    }
}
