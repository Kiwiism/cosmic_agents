package server.agents.observer;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

/** Mutable state owned only by the dedicated observer scheduler. */
final class AgentObserverSession {
    enum Stage {
        STATIONED,
        APPROACH_ROAM,
        MAPLE_CYCLE,
        EXCURSION,
        INVESTIGATING,
        SHADOWING,
        LITH_HARBOR_IDLE
    }

    final int observerId;
    final int watchedId;
    final int world;
    final int channel;
    final AgentRuntimeEntry movementEntry;
    Stage stage = Stage.STATIONED;
    Stage resumeStage = Stage.STATIONED;
    int approachIndex;
    int cycleIndex;
    int destinationMapId = AgentObserverPolicy.STATION_MAP_ID;
    Point destinationPoint;
    long nextDecisionAtMs = Long.MAX_VALUE;
    long investigationRequestedAtMs;
    long lastInvestigationRequestAtMs;
    long investigationStartedAtMs;
    boolean investigationExpressionShown;
    int resumeMapId = AgentObserverPolicy.STATION_MAP_ID;
    int lastObservedMapId = -1;
    boolean bariCompleted;
    boolean handoffTriggered;
    private final Map<Integer, Integer> observationVisits = new HashMap<>();

    AgentObserverSession(int observerId,
                         int watchedId,
                         int world,
                         int channel,
                         AgentRuntimeEntry movementEntry) {
        this.observerId = observerId;
        this.watchedId = watchedId;
        this.world = world;
        this.channel = channel;
        this.movementEntry = movementEntry;
    }

    void requestInvestigation(long nowMs) {
        if (nowMs - lastInvestigationRequestAtMs < AgentObserverPolicy.f1CooldownMs()) {
            return;
        }
        lastInvestigationRequestAtMs = nowMs;
        investigationRequestedAtMs = nowMs;
    }

    void setDestination(int mapId) {
        destinationMapId = mapId;
        destinationPoint = null;
        nextDecisionAtMs = 0;
    }

    int nextObservationVisit(int mapId) {
        int visit = observationVisits.getOrDefault(mapId, 0);
        observationVisits.put(mapId, visit + 1);
        return visit;
    }

    void beginApproachRoute() {
        stage = Stage.APPROACH_ROAM;
        approachIndex = Math.min(1, AgentObserverPolicy.APPROACH_ROUTE.size() - 1);
        setDestination(AgentObserverPolicy.APPROACH_ROUTE.get(approachIndex));
    }
}
