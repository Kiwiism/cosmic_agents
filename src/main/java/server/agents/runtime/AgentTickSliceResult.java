package server.agents.runtime;

public record AgentTickSliceResult(
        AgentTickSliceKind completedSlice,
        AgentTickNextRunHint nextRunHint,
        boolean frameComplete) {
    public AgentTickSliceResult {
        if (completedSlice == null || nextRunHint == null) {
            throw new IllegalArgumentException("Agent tick slice result is incomplete");
        }
        if (frameComplete != (nextRunHint == AgentTickNextRunHint.NORMAL_CADENCE)) {
            throw new IllegalArgumentException("Agent tick completion and next-run hint disagree");
        }
    }
}
