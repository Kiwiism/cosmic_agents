package server.agents.runtime.activity.control;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldShadowReport;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session-local scheduler diagnostics; it deliberately performs no persistence. */
public final class AgentWorldDirectorObserveState {
    public static final AgentCapabilityStateKey<AgentWorldDirectorObserveState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.world-director-observe",
                    AgentWorldDirectorObserveState.class, AgentWorldDirectorObserveState::new);

    private AgentWorldDirectorMode mode = AgentWorldDirectorMode.DISABLED;
    private long intervalMs = 5_000L;
    private long lastSampleAtMs;
    private long sampleCount;
    private AgentActivityKind selectedKind;
    private String selectedProposalId = "";
    private String evidence = "";

    public synchronized void configure(AgentWorldDirectorMode nextMode, long nextIntervalMs) {
        if (nextMode == null || !nextMode.isObservationOnly() || nextIntervalMs < 1_000L) {
            throw new IllegalArgumentException("Observe mode and interval of at least one second are required");
        }
        mode = nextMode;
        intervalMs = nextIntervalMs;
    }

    public synchronized void disable() {
        mode = AgentWorldDirectorMode.DISABLED;
    }

    public synchronized boolean due(long nowMs) {
        return mode.isObservationOnly()
                && (lastSampleAtMs == 0L || nowMs - lastSampleAtMs >= intervalMs);
    }

    public synchronized void sampled(AgentWorldShadowReport report, long nowMs) {
        lastSampleAtMs = nowMs;
        sampleCount++;
        selectedKind = report.decision().kind();
        selectedProposalId = report.decision().proposalId();
        evidence = report.decision().evidence();
    }

    public synchronized void failed(String reason, long nowMs) {
        lastSampleAtMs = nowMs;
        evidence = reason == null ? "" : reason.trim();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mode, intervalMs, lastSampleAtMs, sampleCount,
                selectedKind, selectedProposalId, evidence);
    }

    public record Snapshot(
            AgentWorldDirectorMode mode,
            long intervalMs,
            long lastSampleAtMs,
            long sampleCount,
            AgentActivityKind selectedKind,
            String selectedProposalId,
            String evidence) { }
}
