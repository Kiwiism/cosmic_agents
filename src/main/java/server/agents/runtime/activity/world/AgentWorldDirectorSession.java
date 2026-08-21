package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;

/** Durable per-Agent Director state. Preparation never advances it into an owning phase. */
public record AgentWorldDirectorSession(
        int schemaVersion,
        int agentId,
        String goalId,
        AgentWorldDirectorMode mode,
        AgentWorldDirectorPhase phase,
        AgentActivityKind observedActivityKind,
        String observedSessionId,
        String selectedProposalId,
        String activeHandoffId,
        Map<String, Long> cooldownUntilMs,
        long startedAtMs,
        long updatedAtMs,
        long observationCount,
        String lastReason) {

    public AgentWorldDirectorSession {
        if (schemaVersion != 1 || agentId <= 0 || mode == null || phase == null
                || startedAtMs < 0L || updatedAtMs < startedAtMs || observationCount < 0L) {
            throw new IllegalArgumentException("valid World Director session state is required");
        }
        goalId = normalize(goalId);
        observedSessionId = normalize(observedSessionId);
        selectedProposalId = normalize(selectedProposalId);
        activeHandoffId = normalize(activeHandoffId);
        lastReason = normalize(lastReason);
        cooldownUntilMs = Map.copyOf(cooldownUntilMs == null ? Map.of() : cooldownUntilMs);
    }

    public static AgentWorldDirectorSession shadow(int agentId, long nowMs) {
        return new AgentWorldDirectorSession(1, agentId, "observe-level-30-readiness",
                AgentWorldDirectorMode.SHADOW, AgentWorldDirectorPhase.OBSERVING,
                null, "", "", "", Map.of(), nowMs, nowMs, 0L,
                "shadow observation explicitly enabled; live control remains disabled");
    }

    public AgentWorldDirectorSession observe(
            AgentWorldActivityDecision decision, AgentWorldContext context, long nowMs) {
        if (mode != AgentWorldDirectorMode.SHADOW || phase != AgentWorldDirectorPhase.OBSERVING) {
            throw new IllegalStateException("only an observing shadow session accepts samples");
        }
        return new AgentWorldDirectorSession(schemaVersion, agentId, goalId, mode, phase,
                context.currentActivityKind(), context.currentSessionId(),
                decision == null ? "" : decision.proposalId(), "", cooldownUntilMs,
                startedAtMs, nowMs, observationCount + 1L,
                decision == null ? "no shadow decision" : decision.evidence());
    }

    public AgentWorldDirectorSession pause(String reason, long nowMs) {
        return new AgentWorldDirectorSession(schemaVersion, agentId, goalId, mode,
                AgentWorldDirectorPhase.PAUSED, observedActivityKind, observedSessionId,
                selectedProposalId, activeHandoffId, cooldownUntilMs, startedAtMs, nowMs,
                observationCount, reason);
    }

    /** Hard safety gate used by future bootstraps before live integration exists. */
    public boolean mayOwnActivity() {
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
