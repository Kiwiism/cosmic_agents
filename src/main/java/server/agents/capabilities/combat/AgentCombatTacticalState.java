package server.agents.capabilities.combat;

/** Ephemeral combat-policy state. It is safe to discard and reconstruct. */
public final class AgentCombatTacticalState {
    private int leasedMapId = -1;
    private int leasedRegionId = -1;
    private long leaseExpiresAtMs;
    private int incidentalKillStreak;
    private int incidentalKillsOnLease;
    private AgentCombatCandidateClass selectedClass = AgentCombatCandidateClass.UNRELATED;
    private AgentCombatDecisionReason lastDecision = AgentCombatDecisionReason.CLOSEST_ELIGIBLE;
    private int lastSelectedMobId;
    private long lastDecisionAtMs;
    private AgentCombatDecisionReason lastShadowDecision;
    private int lastShadowCandidateCount;

    public synchronized boolean canSweep(int mapId, int regionId, long nowMs) {
        refreshLease(mapId, regionId, nowMs);
        return incidentalKillStreak < AgentCombatPolicyConfig.maxConsecutiveIncidentalKills()
                && incidentalKillsOnLease
                < AgentCombatPolicyConfig.maxIncidentalKillsPerPlatformLease();
    }

    public synchronized void selected(int mapId,
                                      int regionId,
                                      int mobId,
                                      AgentCombatCandidateClass candidateClass,
                                      AgentCombatDecisionReason reason,
                                      long nowMs) {
        refreshLease(mapId, regionId, nowMs);
        selectedClass = candidateClass;
        lastDecision = reason;
        lastSelectedMobId = mobId;
        lastDecisionAtMs = nowMs;
    }

    public synchronized void killed(int mapId, int mobId, boolean required, long nowMs) {
        if (mapId != leasedMapId || nowMs >= leaseExpiresAtMs) {
            clearLease();
        }
        if (required) {
            incidentalKillStreak = 0;
        } else {
            incidentalKillStreak++;
            incidentalKillsOnLease++;
        }
        if (mobId == lastSelectedMobId) {
            selectedClass = required
                    ? AgentCombatCandidateClass.REQUIRED
                    : AgentCombatCandidateClass.INCIDENTAL;
        }
    }

    public synchronized void shadowEvaluated(AgentCombatDecisionReason reason, int candidateCount) {
        lastShadowDecision = reason;
        lastShadowCandidateCount = candidateCount;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(leasedMapId, leasedRegionId, leaseExpiresAtMs,
                incidentalKillStreak, incidentalKillsOnLease, selectedClass,
                lastDecision, lastSelectedMobId, lastDecisionAtMs,
                lastShadowDecision, lastShadowCandidateCount);
    }

    public synchronized void clear() {
        clearLease();
        selectedClass = AgentCombatCandidateClass.UNRELATED;
        lastDecision = AgentCombatDecisionReason.CLOSEST_ELIGIBLE;
        lastSelectedMobId = 0;
        lastDecisionAtMs = 0L;
        lastShadowDecision = null;
        lastShadowCandidateCount = 0;
    }

    private void refreshLease(int mapId, int regionId, long nowMs) {
        if (mapId != leasedMapId || regionId != leasedRegionId || nowMs >= leaseExpiresAtMs) {
            leasedMapId = mapId;
            leasedRegionId = regionId;
            leaseExpiresAtMs = nowMs + AgentCombatPolicyConfig.platformLeaseMs();
            incidentalKillsOnLease = 0;
        }
    }

    private void clearLease() {
        leasedMapId = -1;
        leasedRegionId = -1;
        leaseExpiresAtMs = 0L;
        incidentalKillStreak = 0;
        incidentalKillsOnLease = 0;
    }

    public record Snapshot(int leasedMapId,
                           int leasedRegionId,
                           long leaseExpiresAtMs,
                           int incidentalKillStreak,
                           int incidentalKillsOnLease,
                           AgentCombatCandidateClass selectedClass,
                           AgentCombatDecisionReason lastDecision,
                           int lastSelectedMobId,
                           long lastDecisionAtMs,
                           AgentCombatDecisionReason lastShadowDecision,
                           int lastShadowCandidateCount) {
    }
}
