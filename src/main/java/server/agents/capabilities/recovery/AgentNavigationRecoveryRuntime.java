package server.agents.capabilities.recovery;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Serializes navigation recovery and tracks escalation for non-physical teleports. */
public final class AgentNavigationRecoveryRuntime {
    private static final long RECOVERY_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.recovery.AgentNavigationRecoveryRuntime.RECOVERY_LEASE_MS");
    private static final long POST_TRANSITION_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.recovery.AgentNavigationRecoveryRuntime.POST_TRANSITION_GRACE_MS");
    private static final int TELEPORT_ATTEMPTS_REQUIRED = config.AgentTuning.intValue(
            "server.agents.capabilities.recovery.AgentNavigationRecoveryRuntime.TELEPORT_ATTEMPTS_REQUIRED");
    private static final AgentCapabilityStateKey<State> STATE_KEY = new AgentCapabilityStateKey<>(
            "recovery.navigation", State.class, State::new);

    private AgentNavigationRecoveryRuntime() {
    }

    public static boolean tryAcquire(AgentRuntimeEntry entry, String owner, long nowMs) {
        if (entry == null) {
            return false;
        }
        State state = entry.capabilityStates().require(STATE_KEY);
        synchronized (state) {
            if (nowMs < state.leaseUntilMs) {
                return false;
            }
            state.owner = owner == null ? "navigation-recovery" : owner;
            state.leaseUntilMs = nowMs + Math.max(1L, RECOVERY_LEASE_MS);
            state.attempts++;
            return true;
        }
    }

    public static boolean active(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null) {
            return false;
        }
        State state = entry.capabilityStates().require(STATE_KEY);
        synchronized (state) {
            return nowMs < state.leaseUntilMs;
        }
    }

    public static boolean teleportEscalationReady(AgentRuntimeEntry entry, long nowMs) {
        State state = entry.capabilityStates().require(STATE_KEY);
        synchronized (state) {
            return state.attempts >= Math.max(1, TELEPORT_ATTEMPTS_REQUIRED)
                    && (state.lastTransitionAtMs <= 0L
                    || nowMs - state.lastTransitionAtMs >= Math.max(0L, POST_TRANSITION_GRACE_MS));
        }
    }

    public static void recordTransition(AgentRuntimeEntry entry, long nowMs) {
        State state = entry.capabilityStates().require(STATE_KEY);
        synchronized (state) {
            state.lastTransitionAtMs = nowMs;
            state.attempts = 0;
            state.leaseUntilMs = 0L;
            state.owner = "";
        }
    }

    public static void recordProgress(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        State state = entry.capabilityStates().require(STATE_KEY);
        synchronized (state) {
            state.attempts = 0;
            state.leaseUntilMs = 0L;
            state.owner = "";
        }
    }

    private static final class State {
        private String owner = "";
        private long leaseUntilMs;
        private long lastTransitionAtMs;
        private int attempts;
    }
}
