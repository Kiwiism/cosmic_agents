package server.agents.capabilities.navigation;

public final class AgentMapleIslandTravelState {
    record HopDecision(AgentMapleIslandTravelSettings settings, long sequence) {
    }

    private AgentMapleIslandTravelSettings settings = AgentMapleIslandTravelSettings.disabled();
    private long nextHopDecisionAtMs;
    private long hopCooldownUntilMs;
    private long hopDecisionSequence;

    synchronized void configure(AgentMapleIslandTravelSettings configured) {
        settings = configured == null ? AgentMapleIslandTravelSettings.disabled() : configured;
        nextHopDecisionAtMs = 0L;
        hopCooldownUntilMs = 0L;
        hopDecisionSequence = 0L;
    }

    synchronized void clear() {
        configure(AgentMapleIslandTravelSettings.disabled());
    }

    synchronized AgentMapleIslandTravelSettings settings() {
        return settings;
    }

    synchronized HopDecision beginHopDecision(long nowMs) {
        if (!settings.travelHopsEnabled()
                || nowMs < nextHopDecisionAtMs
                || nowMs < hopCooldownUntilMs) {
            return null;
        }
        nextHopDecisionAtMs = saturatedAdd(nowMs, settings.travelHopDecisionIntervalMs());
        return new HopDecision(settings, hopDecisionSequence++);
    }

    synchronized void markHopStarted(long nowMs) {
        if (!settings.travelHopsEnabled()) {
            return;
        }
        hopCooldownUntilMs = saturatedAdd(nowMs, settings.travelHopCooldownMs());
        nextHopDecisionAtMs = Math.max(nextHopDecisionAtMs, hopCooldownUntilMs);
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
