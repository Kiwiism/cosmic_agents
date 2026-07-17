package server.agents.plans.mapleisland;

import server.agents.capabilities.navigation.AgentTravelVariationRuntime;
import server.agents.capabilities.navigation.AgentTravelVariationSettings;
import server.agents.capabilities.navigation.AgentPortalRoutePlan;
import server.agents.capabilities.navigation.AgentPortalRoutePolicy;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;

/** Maple Island's seeded route families across Split Road of Destiny. */
public final class AgentSplitRoadRouteService implements AgentPortalRoutePolicy {
    public static final AgentSplitRoadRouteService INSTANCE = new AgentSplitRoadRouteService();
    public static final int MAP_ID = 1020000;
    public static final int SOUTHPERRY_MAP_ID = 2000000;
    public static final int UPPER_PLATFORM_PORTAL_ID = 4;
    private static final long ROUTE_DOMAIN = 0x53504C4954524F44L;
    private static final Point UPPER_CENTER = new Point(350, 39);
    private static final Point UPPER_LEFT = new Point(-20, 54);
    private static final Point UPPER_RIGHT = new Point(500, 49);

    public enum Variant {
        GROUND,
        UPPER_CENTER,
        UPPER_RIGHT,
        UPPER_SCENIC
    }

    public record Plan(Variant variant, AgentPortalRoutePlan route) {
        public boolean usesInternalPortal() {
            return route.usesInternalPortal();
        }

        public List<Point> waypoints() {
            return route.waypoints();
        }
    }

    private AgentSplitRoadRouteService() {
    }

    @Override
    public AgentPortalRoutePlan plan(AgentRuntimeEntry entry, int sourceMapId, int destinationMapId) {
        if (sourceMapId != MAP_ID || destinationMapId != SOUTHPERRY_MAP_ID) {
            return AgentPortalRoutePlan.DIRECT;
        }
        return select(entry).route();
    }

    public Plan select(AgentRuntimeEntry entry) {
        AgentTravelVariationSettings settings = AgentTravelVariationRuntime.settings(entry);
        if (!settings.routeVariationEnabled()) {
            return groundPlan();
        }
        long agentId = AgentRuntimeIdentityRuntime.hasBot(entry)
                ? Integer.toUnsignedLong(AgentRuntimeIdentityRuntime.bot(entry).getId()) : 0L;
        int sample = (int) Long.remainderUnsigned(
                mix(settings.seed() ^ agentId ^ ROUTE_DOMAIN), 100L);
        if (sample < 45) {
            return groundPlan();
        }
        if (sample < 65) {
            return route(Variant.UPPER_CENTER, List.of(UPPER_CENTER));
        }
        if (sample < 90) {
            return route(Variant.UPPER_RIGHT, List.of(UPPER_RIGHT));
        }
        return route(Variant.UPPER_SCENIC, List.of(UPPER_LEFT, UPPER_RIGHT));
    }

    private static Plan route(Variant variant, List<Point> waypoints) {
        return new Plan(variant, new AgentPortalRoutePlan(
                UPPER_PLATFORM_PORTAL_ID, waypoints, "Split Road upper-platform route"));
    }

    private static Plan groundPlan() {
        return new Plan(Variant.GROUND, AgentPortalRoutePlan.DIRECT);
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
