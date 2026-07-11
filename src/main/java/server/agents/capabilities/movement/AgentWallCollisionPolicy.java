package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationWalkRegionLookupService;
import server.maps.Foothold;
import server.maps.MapleMap;

/** Client-compatible zMass scoping for airborne foothold-wall collisions. */
public final class AgentWallCollisionPolicy {
    public static final int ALL_GROUPS = Integer.MIN_VALUE;
    public static final int UNKNOWN_GROUP = -1;

    private AgentWallCollisionPolicy() {
    }

    public static boolean collides(MapleMap map, Foothold wall, int moverZMass) {
        if (wall == null || !wall.isWall()) {
            return false;
        }
        int baseZMass = baseZMass(map);
        int wallZMass = wall.getZMass();
        if (moverZMass == ALL_GROUPS || wallZMass < 0 || baseZMass < 0) {
            return true;
        }
        return wallZMass == baseZMass || wallZMass == moverZMass;
    }

    public static int moverZMassForRegion(MapleMap map, int regionId) {
        if (map == null || regionId < 0) {
            return UNKNOWN_GROUP;
        }
        AgentNavigationWalkRegionLookupService.WalkRegionLookup lookup =
                AgentNavigationWalkRegionLookupService.resolveWalkRegionLookup(map);
        if (lookup == null) {
            return UNKNOWN_GROUP;
        }
        AgentNavigationGraph.Region region = lookup.regionsById().get(regionId);
        if (region == null || region.isRopeRegion || region.segments.isEmpty()) {
            return UNKNOWN_GROUP;
        }
        Foothold foothold = lookup.footholdsById().get(region.segments.getFirst().footholdId);
        return foothold == null ? UNKNOWN_GROUP : foothold.getZMass();
    }

    public static int moverZMassAt(MapleMap map, java.awt.Point position) {
        Foothold foothold = AgentGroundingService.findGroundFoothold(map, position);
        if (foothold == null) {
            return UNKNOWN_GROUP;
        }
        java.awt.Point ground = AgentGroundingService.findGroundPoint(map, position);
        if (ground == null || Math.abs(ground.y - position.y) > AgentMovementPhysicsConfig.configuredMaxSlopeUp()) {
            return UNKNOWN_GROUP;
        }
        return foothold.getZMass();
    }

    private static int baseZMass(MapleMap map) {
        if (map == null || map.getFootholds() == null) {
            return UNKNOWN_GROUP;
        }
        int base = UNKNOWN_GROUP;
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (foothold.getZMass() >= 0 && (base < 0 || foothold.getZMass() < base)) {
                base = foothold.getZMass();
            }
        }
        return base;
    }
}
