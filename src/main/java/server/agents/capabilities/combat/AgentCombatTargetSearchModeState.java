package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.List;
import java.util.Objects;

/** Per-Agent quest target-search phase and compact decision evidence. */
public final class AgentCombatTargetSearchModeState {
    public static final AgentCapabilityStateKey<AgentCombatTargetSearchModeState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.target-search-mode",
                    AgentCombatTargetSearchModeState.class,
                    AgentCombatTargetSearchModeState::new);

    private int mapId = Integer.MIN_VALUE;
    private String objectiveId = "";
    private AgentCombatTargetSearchMode mode = AgentCombatTargetSearchMode.LOCAL_CLEAR;
    private String transitionReason = "initial local search";
    private long changedAtMs;
    private int emptyPreferredScans;
    private int destinationRegionId = -1;
    private int localCandidateCount;
    private int preferredCandidateCount;
    private List<RankedRegion> rankedRegions = List.of();

    public synchronized void synchronizeScope(int currentMapId,
                                              String currentObjectiveId,
                                              long nowMs) {
        String normalizedObjectiveId = Objects.requireNonNullElse(currentObjectiveId, "");
        if (mapId == currentMapId && objectiveId.equals(normalizedObjectiveId)) {
            return;
        }
        mapId = currentMapId;
        objectiveId = normalizedObjectiveId;
        mode = AgentCombatTargetSearchMode.LOCAL_CLEAR;
        transitionReason = "map or objective changed";
        changedAtMs = nowMs;
        emptyPreferredScans = 0;
        destinationRegionId = -1;
        localCandidateCount = 0;
        preferredCandidateCount = 0;
        rankedRegions = List.of();
    }

    public synchronized boolean observeLocalPreferred(boolean present,
                                                      int emptyScanThreshold,
                                                      long nowMs) {
        if (present) {
            emptyPreferredScans = 0;
            if (mode != AgentCombatTargetSearchMode.REGION_HARVEST) {
                transition(AgentCombatTargetSearchMode.LOCAL_CLEAR,
                        "required target available locally", -1, nowMs);
            }
            return false;
        }
        emptyPreferredScans++;
        return emptyPreferredScans >= Math.max(1, emptyScanThreshold);
    }

    public synchronized void enter(AgentCombatTargetSearchMode nextMode,
                                   String reason,
                                   int targetRegionId,
                                   long nowMs) {
        transition(nextMode, reason, targetRegionId, nowMs);
    }

    public synchronized void recordEvidence(int localCandidates,
                                            int preferredCandidates,
                                            List<RankedRegion> regions) {
        localCandidateCount = Math.max(0, localCandidates);
        preferredCandidateCount = Math.max(0, preferredCandidates);
        rankedRegions = regions == null ? List.of() : List.copyOf(regions);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mapId, objectiveId, mode, transitionReason, changedAtMs,
                emptyPreferredScans, destinationRegionId, localCandidateCount,
                preferredCandidateCount, rankedRegions);
    }

    private void transition(AgentCombatTargetSearchMode nextMode,
                            String reason,
                            int targetRegionId,
                            long nowMs) {
        AgentCombatTargetSearchMode normalizedMode = nextMode == null
                ? AgentCombatTargetSearchMode.LOCAL_CLEAR : nextMode;
        String normalizedReason = Objects.requireNonNullElse(reason, "");
        if (mode != normalizedMode || destinationRegionId != targetRegionId
                || !transitionReason.equals(normalizedReason)) {
            changedAtMs = nowMs;
        }
        mode = normalizedMode;
        transitionReason = normalizedReason;
        destinationRegionId = targetRegionId;
    }

    public record RankedRegion(int regionId,
                               long routeScore,
                               long localScore,
                               int targetObjectId,
                               int mobId) {
    }

    public record Snapshot(int mapId,
                           String objectiveId,
                           AgentCombatTargetSearchMode mode,
                           String transitionReason,
                           long changedAtMs,
                           int emptyPreferredScans,
                           int destinationRegionId,
                           int localCandidateCount,
                           int preferredCandidateCount,
                           List<RankedRegion> rankedRegions) {
    }
}
