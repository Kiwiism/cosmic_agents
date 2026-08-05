package server.agents.capabilities.looting;

import server.agents.runtime.state.AgentCapabilityStateKey;

/**
 * Ephemeral evidence for the last loot policy decision. Recording this state
 * cannot alter pickup eligibility, movement, inventory, or combat decisions.
 */
public final class AgentLootDecisionTraceState {
    public static final AgentCapabilityStateKey<AgentLootDecisionTraceState> STATE_KEY =
            new AgentCapabilityStateKey<>("looting.decision-trace",
                    AgentLootDecisionTraceState.class, AgentLootDecisionTraceState::new);

    private Mode mode = Mode.NONE;
    private Outcome outcome = Outcome.NONE;
    private long recordedAtMs;
    private int recentKillCount;
    private boolean combatTargetPresent;
    private int targetObjectId;
    private long requiredDropAgeMs;
    private long observedDropAgeMs;

    public synchronized void record(Mode mode,
                                    Outcome outcome,
                                    long recordedAtMs,
                                    int recentKillCount,
                                    boolean combatTargetPresent,
                                    int targetObjectId,
                                    long requiredDropAgeMs,
                                    long observedDropAgeMs) {
        this.mode = mode == null ? Mode.NONE : mode;
        this.outcome = outcome == null ? Outcome.NONE : outcome;
        this.recordedAtMs = recordedAtMs;
        this.recentKillCount = Math.max(0, recentKillCount);
        this.combatTargetPresent = combatTargetPresent;
        this.targetObjectId = Math.max(0, targetObjectId);
        this.requiredDropAgeMs = Math.max(0L, requiredDropAgeMs);
        this.observedDropAgeMs = Math.max(0L, observedDropAgeMs);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mode, outcome, recordedAtMs, recentKillCount,
                combatTargetPresent, targetObjectId, requiredDropAgeMs,
                observedDropAgeMs);
    }

    public enum Mode {
        NONE,
        PASSIVE,
        POST_KILL_MELEE,
        POST_KILL_RANGED,
        PRE_EXIT
    }

    public enum Outcome {
        NONE,
        INHIBITED,
        TRADE_ACTIVE,
        POLICY_DEFERRED,
        WAITING_FOR_DROP,
        NO_ELIGIBLE_DROP,
        INELIGIBLE,
        TARGET_SELECTED,
        PICKED_UP,
        INVENTORY_FULL
    }

    public record Snapshot(Mode mode,
                           Outcome outcome,
                           long recordedAtMs,
                           int recentKillCount,
                           boolean combatTargetPresent,
                           int targetObjectId,
                           long requiredDropAgeMs,
                           long observedDropAgeMs) {
    }
}
