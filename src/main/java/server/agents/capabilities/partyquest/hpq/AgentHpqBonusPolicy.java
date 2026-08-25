package server.agents.capabilities.partyquest.hpq;

/** HPQ-only choice and timing policy for the optional Pig Town bonus. */
final class AgentHpqBonusPolicy {
    private AgentHpqBonusPolicy() {
    }

    static AgentHpqSession.BonusMode defaultMode() {
        return config.AgentTuning.booleanValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqBonusPolicy.ENTER_BY_DEFAULT")
                ? AgentHpqSession.BonusMode.ENTER : AgentHpqSession.BonusMode.SKIP;
    }

    static long dwellMs() {
        return Math.max(0L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqBonusPolicy.DWELL_MS"));
    }

    static long humanDecisionMs() {
        return Math.max(0L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqBonusPolicy.HUMAN_DECISION_MS"));
    }

}
