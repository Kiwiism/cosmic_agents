package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Empty-scan and borrowing state for one coordinator-issued combat region lease. */
public final class AgentCombatRegionAssignmentState {
    public static final AgentCapabilityStateKey<AgentCombatRegionAssignmentState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.region-assignment",
                    AgentCombatRegionAssignmentState.class,
                    AgentCombatRegionAssignmentState::new);

    private String assignmentId = "";
    private int emptyScans;
    private long borrowUntilMs;

    public synchronized boolean observe(
            String currentAssignmentId,
            boolean assignedTargetPresent,
            int emptyScanThreshold,
            long nowMs,
            long borrowDurationMs) {
        if (!assignmentId.equals(currentAssignmentId)) {
            assignmentId = currentAssignmentId;
            emptyScans = 0;
            borrowUntilMs = 0L;
        }
        if (assignedTargetPresent) {
            emptyScans = 0;
            borrowUntilMs = 0L;
            return false;
        }
        if (nowMs < borrowUntilMs) {
            return true;
        }
        emptyScans++;
        if (emptyScans >= Math.max(1, emptyScanThreshold)) {
            emptyScans = 0;
            borrowUntilMs = nowMs + Math.max(1L, borrowDurationMs);
            return true;
        }
        return false;
    }

    public synchronized Snapshot snapshot(long nowMs) {
        return new Snapshot(assignmentId, emptyScans, Math.max(0L, borrowUntilMs - nowMs));
    }

    public record Snapshot(String assignmentId, int emptyScans, long borrowRemainingMs) {
    }
}
