package server.agents.capabilities.navigation;

public final class AgentTravelVariationState {
    record HopDecision(AgentTravelVariationSettings settings, long sequence) {}
    private AgentTravelVariationSettings settings = AgentTravelVariationSettings.disabled();
    private long nextHopDecisionAtMs;
    private long hopCooldownUntilMs;
    private long hopDecisionSequence;
    synchronized void configure(AgentTravelVariationSettings configured) {
        settings = configured == null ? AgentTravelVariationSettings.disabled() : configured;
        nextHopDecisionAtMs = hopCooldownUntilMs = hopDecisionSequence = 0L;
    }
    synchronized void clear() { configure(AgentTravelVariationSettings.disabled()); }
    synchronized AgentTravelVariationSettings settings() { return settings; }
    synchronized HopDecision beginHopDecision(long nowMs) {
        if (!settings.travelHopsEnabled() || nowMs < nextHopDecisionAtMs || nowMs < hopCooldownUntilMs) return null;
        nextHopDecisionAtMs = saturatedAdd(nowMs, settings.travelHopDecisionIntervalMs());
        return new HopDecision(settings, hopDecisionSequence++);
    }
    synchronized void markHopStarted(long nowMs) {
        if (!settings.travelHopsEnabled()) return;
        hopCooldownUntilMs = saturatedAdd(nowMs, settings.travelHopCooldownMs());
        nextHopDecisionAtMs = Math.max(nextHopDecisionAtMs, hopCooldownUntilMs);
    }
    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
