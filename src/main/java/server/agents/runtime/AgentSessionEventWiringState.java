package server.agents.runtime;

import server.agents.events.AgentEventSubscription;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.List;

/** Owns all event subscriptions created for one Agent session. */
public final class AgentSessionEventWiringState implements AutoCloseable {
    public static final AgentCapabilityStateKey<AgentSessionEventWiringState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.event-wiring", AgentSessionEventWiringState.class,
                    AgentSessionEventWiringState::new);

    private List<AgentEventSubscription> subscriptions = List.of();
    private boolean wired;

    public synchronized boolean wired() {
        return wired;
    }

    public synchronized void attach(List<AgentEventSubscription> subscriptions) {
        if (wired) {
            subscriptions.forEach(AgentEventSubscription::close);
            return;
        }
        this.subscriptions = List.copyOf(subscriptions);
        wired = true;
    }

    @Override
    public synchronized void close() {
        subscriptions.forEach(AgentEventSubscription::close);
        subscriptions = List.of();
        wired = false;
    }
}
