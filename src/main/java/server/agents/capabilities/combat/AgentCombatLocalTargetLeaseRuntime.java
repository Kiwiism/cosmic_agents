package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

/** Integration boundary for the local-target lease policy. */
final class AgentCombatLocalTargetLeaseRuntime {
    private AgentCombatLocalTargetLeaseRuntime() {
    }

    static void observePosition(AgentRuntimeEntry entry, Character agent, Point position, long nowMs) {
        if (entry == null || agent == null || agent.getMap() == null || position == null) {
            return;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        int regionId = graph == null ? -1 : AgentNavigationRegionService.resolveCurrentRegionId(
                graph, entry, agent.getMap(), position);
        AgentCombatLocalTargetLeaseState lease = state(entry);
        lease.observeRegion(agent.getMapId(), objectiveId(entry), regionId, nowMs,
                AgentCombatPolicyConfig.localTargetLeaseMs(),
                AgentCombatPolicyConfig.localTargetLeaseKills());
        AgentCombatLocalTargetLeaseState.Snapshot snapshot = lease.snapshot(nowMs);
        if (snapshot.phase() == AgentCombatLocalTargetLeaseState.Phase.ACTIVE) {
            entry.capabilityStates().require(AgentCombatTargetSearchModeState.STATE_KEY)
                    .enter(AgentCombatTargetSearchMode.REGION_HARVEST,
                            "reached map-wide destination; harvesting local population",
                            snapshot.destinationRegionId(), nowMs);
        }
    }

    static boolean allowsMapWidePromotion(AgentRuntimeEntry entry,
                                          Character agent,
                                          boolean hasSuitableLocalObjective,
                                          long nowMs) {
        if (entry == null || agent == null) {
            return true;
        }
        observePosition(entry, agent, agent.getPosition(), nowMs);
        AgentCombatLocalTargetLeaseState lease = state(entry);
        AgentCombatLocalTargetLeaseState.Snapshot snapshot = lease.snapshot(nowMs);
        if (snapshot.phase() == AgentCombatLocalTargetLeaseState.Phase.TRAVELLING) {
            if (hasSuitableLocalObjective
                    || !retainedTravelTargetMatches(entry, agent, snapshot)) {
                lease.cancelTravel();
            }
        }
        return lease.scan(hasSuitableLocalObjective, nowMs,
                AgentCombatPolicyConfig.localTargetLeaseEmptyScans());
    }

    static void beganMapWideTravel(AgentRuntimeEntry entry, Character agent, int targetRegionId) {
        if (entry != null && agent != null) {
            state(entry).beginMapWideTravel(
                    agent.getMapId(), objectiveId(entry), targetRegionId,
                    System.currentTimeMillis(), AgentCombatPolicyConfig.localTargetLeaseMs());
        }
    }

    static Monster retainedTravelTarget(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return null;
        }
        AgentCombatLocalTargetLeaseState.Snapshot snapshot = state(entry).snapshot(nowMs);
        return snapshot.phase() == AgentCombatLocalTargetLeaseState.Phase.TRAVELLING
                && retainedTravelTargetMatches(entry, agent, snapshot)
                ? AgentGrindTargetStateRuntime.target(entry) : null;
    }

    static void cancelTravel(AgentRuntimeEntry entry) {
        if (entry != null) {
            state(entry).cancelTravel();
        }
    }

    static void recordKill(AgentRuntimeEntry entry, int mapId, String objectiveId,
                           boolean eligibleLocalKill, long nowMs) {
        if (entry != null && eligibleLocalKill) {
            state(entry).recordLocalKill(mapId, objectiveId, nowMs);
        }
    }

    private static AgentCombatLocalTargetLeaseState state(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatLocalTargetLeaseState.STATE_KEY);
    }

    private static boolean retainedTravelTargetMatches(
            AgentRuntimeEntry entry,
            Character agent,
            AgentCombatLocalTargetLeaseState.Snapshot lease) {
        Monster target = AgentGrindTargetStateRuntime.target(entry);
        if (target == null || !target.isAlive() || target.getMap() != agent.getMap()) {
            return false;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        return graph == null || AgentNavigationRegionService.resolveTargetRegionId(
                graph, entry, agent.getMap(), target.getPosition()) == lease.destinationRegionId();
    }

    private static String objectiveId(AgentRuntimeEntry entry) {
        return AgentProgressionEventPublisher.objectiveId(entry);
    }
}
