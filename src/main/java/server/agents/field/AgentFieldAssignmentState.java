package server.agents.field;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Per-Agent projection of its current field intent and territory. */
public final class AgentFieldAssignmentState {
    public static final AgentCapabilityStateKey<AgentFieldAssignmentState> STATE_KEY =
            new AgentCapabilityStateKey<>("field.assignment", AgentFieldAssignmentState.class,
                    AgentFieldAssignmentState::new);

    private String sessionId = "";
    private AgentFieldIntent intent;
    private AgentFieldAssignment assignment;
    private long updatedAtMs;

    public synchronized boolean update(
            String currentSessionId,
            AgentFieldIntent currentIntent,
            AgentFieldAssignment currentAssignment,
            long nowMs) {
        String nextSessionId = currentSessionId == null ? "" : currentSessionId;
        boolean changed = !java.util.Objects.equals(sessionId, nextSessionId)
                || intent == null || currentIntent == null
                || intent.type() != currentIntent.type()
                || !sameAssignment(assignment, currentAssignment);
        sessionId = nextSessionId;
        intent = currentIntent;
        assignment = currentAssignment;
        updatedAtMs = Math.max(0L, nowMs);
        return changed;
    }

    public synchronized void clear() {
        sessionId = "";
        intent = null;
        assignment = null;
        updatedAtMs = 0L;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(sessionId, intent, assignment, updatedAtMs);
    }

    public record Snapshot(
            String sessionId,
            AgentFieldIntent intent,
            AgentFieldAssignment assignment,
            long updatedAtMs) {
    }

    private static boolean sameAssignment(
            AgentFieldAssignment left, AgentFieldAssignment right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.partySlot() == right.partySlot()
                && left.cellIds().equals(right.cellIds())
                && left.regionIds().equals(right.regionIds())
                && left.stationId().equals(right.stationId())
                && left.territoryMinX() == right.territoryMinX()
                && left.territoryMaxX() == right.territoryMaxX()
                && left.anchor().equals(right.anchor());
    }
}
