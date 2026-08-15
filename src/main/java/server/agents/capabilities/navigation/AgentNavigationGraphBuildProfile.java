package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Mutable build-time metrics collector, kept separate from graph construction mechanics. */
final class AgentNavigationGraphBuildProfile {
    long buildAnchorPointsNs;
    final int mapId;
    final int totalSpeedStat;
    final int totalJumpStat;
    private final long buildStartedAtNs = System.nanoTime();
    int footholdCount;
    int walkableFootholdCount;
    int ropeCount;
    int regionCount;
    int totalEdgeCount;
    int walkEdgeCount;
    int jumpEdgeCount;
    int dropEdgeCount;
    int climbEdgeCount;
    int portalEdgeCount;
    long collectFootholdsNs;
    long buildRegionsNs;
    long addRopeRegionsNs;
    long buildFeatureXsNs;
    long buildWalkEdgesNs;
    long buildDropEdgesNs;
    long buildJumpEdgesNs;
    long buildRopeEntryEdgesNs;
    long buildRopeExitEdgesNs;
    long buildPortalEdgesNs;
    long jumpSampleCount;
    long jumpCacheHitCount;
    long jumpCacheMissCount;
    long jumpBoundaryRefineProbeCount;
    private final int maxProfiledJumpRegions;
    private final List<AgentNavigationGraphService.JumpRegionProfile> slowestJumpRegions =
            new ArrayList<>();

    AgentNavigationGraphBuildProfile(int mapId,
                                     AgentMovementProfile movementProfile,
                                     int maxProfiledJumpRegions) {
        this.mapId = mapId;
        this.totalSpeedStat = movementProfile.totalSpeedStat();
        this.totalJumpStat = movementProfile.totalJumpStat();
        this.maxProfiledJumpRegions = maxProfiledJumpRegions;
    }

    void recordEdge(AgentNavigationGraph.EdgeType type) {
        totalEdgeCount++;
        switch (type) {
            case WALK -> walkEdgeCount++;
            case JUMP, FLASH_JUMP, TELEPORT -> jumpEdgeCount++;
            case DROP -> dropEdgeCount++;
            case CLIMB -> climbEdgeCount++;
            case PORTAL -> portalEdgeCount++;
        }
    }

    void recordJumpSample(boolean cacheHit) {
        jumpSampleCount++;
        if (cacheHit) {
            jumpCacheHitCount++;
        } else {
            jumpCacheMissCount++;
        }
    }

    void recordJumpBoundaryRefineProbe() {
        jumpBoundaryRefineProbeCount++;
    }

    void recordJumpRegion(AgentNavigationGraphService.JumpRegionProfile profile) {
        slowestJumpRegions.add(profile);
        slowestJumpRegions.sort(Comparator.comparingLong(
                AgentNavigationGraphService.JumpRegionProfile::elapsedNs).reversed());
        if (slowestJumpRegions.size() > maxProfiledJumpRegions) {
            slowestJumpRegions.removeLast();
        }
    }

    AgentNavigationGraphService.GraphBuildReport finish() {
        return new AgentNavigationGraphService.GraphBuildReport(
                mapId,
                totalSpeedStat,
                totalJumpStat,
                footholdCount,
                walkableFootholdCount,
                ropeCount,
                regionCount,
                totalEdgeCount,
                walkEdgeCount,
                jumpEdgeCount,
                dropEdgeCount,
                climbEdgeCount,
                portalEdgeCount,
                buildAnchorPointsNs,
                collectFootholdsNs,
                buildRegionsNs,
                addRopeRegionsNs,
                buildFeatureXsNs,
                buildWalkEdgesNs,
                buildDropEdgesNs,
                buildJumpEdgesNs,
                buildRopeEntryEdgesNs,
                buildRopeExitEdgesNs,
                buildPortalEdgesNs,
                System.nanoTime() - buildStartedAtNs,
                jumpSampleCount,
                jumpCacheHitCount,
                jumpCacheMissCount,
                jumpBoundaryRefineProbeCount,
                slowestJumpRegions);
    }
}
