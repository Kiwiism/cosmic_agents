package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/**
 * Allocation-light, ephemeral evidence describing the last combat target decision.
 * It observes policy execution; it never participates in target selection.
 */
public final class AgentCombatDecisionTraceState {
    public static final AgentCapabilityStateKey<AgentCombatDecisionTraceState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.decision-trace",
                    AgentCombatDecisionTraceState.class, AgentCombatDecisionTraceState::new);

    private Mode mode = Mode.NONE;
    private Outcome outcome = Outcome.NONE;
    private long recordedAtMs;
    private int baseCandidates;
    private int objectiveCandidates;
    private int policyCandidates;
    private int claimCandidates;
    private int scoredCandidates;
    private boolean mapWidePreferredEscalation;
    private boolean rankedVariationConsumed;
    private int selectedObjectId;
    private int selectedMobId;

    public synchronized void record(Mode mode,
                                    Outcome outcome,
                                    long recordedAtMs,
                                    int baseCandidates,
                                    int objectiveCandidates,
                                    int policyCandidates,
                                    int claimCandidates,
                                    int scoredCandidates,
                                    boolean mapWidePreferredEscalation,
                                    boolean rankedVariationConsumed,
                                    int selectedObjectId,
                                    int selectedMobId) {
        this.mode = mode == null ? Mode.NONE : mode;
        this.outcome = outcome == null ? Outcome.NONE : outcome;
        this.recordedAtMs = recordedAtMs;
        this.baseCandidates = Math.max(0, baseCandidates);
        this.objectiveCandidates = Math.max(0, objectiveCandidates);
        this.policyCandidates = Math.max(0, policyCandidates);
        this.claimCandidates = Math.max(0, claimCandidates);
        this.scoredCandidates = Math.max(0, scoredCandidates);
        this.mapWidePreferredEscalation = mapWidePreferredEscalation;
        this.rankedVariationConsumed = rankedVariationConsumed;
        this.selectedObjectId = Math.max(0, selectedObjectId);
        this.selectedMobId = Math.max(0, selectedMobId);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mode, outcome, recordedAtMs, baseCandidates,
                objectiveCandidates, policyCandidates, claimCandidates,
                scoredCandidates, mapWidePreferredEscalation,
                rankedVariationConsumed, selectedObjectId, selectedMobId);
    }

    public enum Mode {
        NONE,
        GRIND,
        PATROL,
        FOLLOW,
        ROUTE_BLOCKER
    }

    public enum Outcome {
        NONE,
        SELECTED,
        PRE_EXIT_LOOT,
        NO_CANDIDATES,
        OBJECTIVE_FILTERED,
        POLICY_FILTERED,
        CLAIMS_FILTERED,
        RESPONSE_DEFERRED,
        GRAPH_UNAVAILABLE,
        UNREACHABLE
    }

    public record Snapshot(Mode mode,
                           Outcome outcome,
                           long recordedAtMs,
                           int baseCandidates,
                           int objectiveCandidates,
                           int policyCandidates,
                           int claimCandidates,
                           int scoredCandidates,
                           boolean mapWidePreferredEscalation,
                           boolean rankedVariationConsumed,
                           int selectedObjectId,
                           int selectedMobId) {
    }
}
