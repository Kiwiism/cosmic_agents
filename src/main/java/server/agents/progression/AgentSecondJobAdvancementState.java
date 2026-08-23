package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.ArrayDeque;
import java.util.List;

public final class AgentSecondJobAdvancementState {
    public enum Phase { READY, LEADER, INSTRUCTOR, TRIAL, EXAMINER, RETURN_TO_LEADER, VERIFY, COMPLETE, BLOCKED }

    public static final AgentCapabilityStateKey<AgentSecondJobAdvancementState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.second-job", AgentSecondJobAdvancementState.class,
                    AgentSecondJobAdvancementState::new);

    private String branchId = "";
    private Phase phase = Phase.READY;
    private String reason = "";
    private long phaseSinceMs;
    private int consecutiveCapabilityFailures;
    private final ArrayDeque<Transition> journal = new ArrayDeque<>();

    public synchronized void begin(String branchId, long nowMs) {
        if (!this.branchId.isBlank() && !this.branchId.equals(branchId)) {
            throw new IllegalStateException("Second-job branch is already committed to " + this.branchId);
        }
        this.branchId = branchId;
        consecutiveCapabilityFailures = 0;
        phase(Phase.READY, "branch committed", nowMs);
    }

    public synchronized void phase(Phase phase, String reason, long nowMs) {
        if (this.phase != phase || journal.isEmpty()) {
            phaseSinceMs = nowMs;
            journal.addLast(new Transition(nowMs, phase, reason == null ? "" : reason));
            while (journal.size() > 64) journal.removeFirst();
        }
        this.phase = phase;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized String branchId() { return branchId; }
    public synchronized Phase phase() { return phase; }
    public synchronized String reason() { return reason; }
    public synchronized long phaseSinceMs() { return phaseSinceMs; }
    public synchronized int capabilityFailure() { return ++consecutiveCapabilityFailures; }
    public synchronized void capabilityProgress() { consecutiveCapabilityFailures = 0; }
    public synchronized List<Transition> journalSnapshot() { return List.copyOf(journal); }

    public record Transition(long occurredAtMs, Phase phase, String reason) { }
}
