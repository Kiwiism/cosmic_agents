package server.agents.plans;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Step-owned phase for the asynchronous Shanks-to-Lith-Harbor crossing. */
public final class AgentSouthperryLithTransferState {
    public static final AgentCapabilityStateKey<AgentSouthperryLithTransferState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "plans.southperry-lith-transfer",
                    AgentSouthperryLithTransferState.class,
                    AgentSouthperryLithTransferState::new);

    public enum Stage {
        CROSSING,
        ARRIVED_IN_LITH,
        TOWNLIFE_READY
    }

    private Stage stage = Stage.CROSSING;

    public synchronized void crossing() {
        stage = Stage.CROSSING;
    }

    public synchronized void arrivedInLith() {
        stage = Stage.ARRIVED_IN_LITH;
    }

    public synchronized void townLifeReady() {
        stage = Stage.TOWNLIFE_READY;
    }

    public synchronized Stage stage() {
        return stage;
    }
}
