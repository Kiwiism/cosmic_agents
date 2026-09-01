package server.agents.capabilities.partyquest.epq;

/** Bounded per-stage liveness guard; the 30-minute authored event timer remains authoritative. */
final class AgentEpqWatchdogRuntime {
    private static final long AUTONOMOUS_IDLE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqWatchdogRuntime.AUTONOMOUS_IDLE_MS");
    private static final long HUMAN_ASSISTED_IDLE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqWatchdogRuntime.HUMAN_ASSISTED_IDLE_MS");

    private AgentEpqWatchdogRuntime() { }

    static void tick(AgentEpqSession session, long nowMs) {
        if (session == null || session.terminal() || session.paused()) return;
        long limit = session.mode() == AgentEpqSession.Mode.HUMAN_ASSISTED
                ? HUMAN_ASSISTED_IDLE_MS : AUTONOMOUS_IDLE_MS;
        if (nowMs - session.lastProgressAtMs() >= limit) {
            session.fail("EPQ stalled in " + session.phase() + " for "
                    + (limit / 1_000L) + " seconds", nowMs);
        }
    }
}
