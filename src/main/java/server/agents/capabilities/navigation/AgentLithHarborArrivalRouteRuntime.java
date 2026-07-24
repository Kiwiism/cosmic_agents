package server.agents.capabilities.navigation;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.List;

/**
 * Shared Lith Harbor arrival policy for Shanks travel and Victoria test runs.
 *
 * <p>The ordinary spawn portal (portal 0) is intentionally excluded: it lies in a region that
 * cannot safely reach Olaf until the navigation graph has finished loading. Arrival is instead
 * placed on navigable ship platforms. Hidden portals provide the fallback route while the graph
 * warms; ordinary navigation takes over once the Agent reaches connected town ground.</p>
 */
public final class AgentLithHarborArrivalRouteRuntime {
    public static final int LITH_HARBOR_MAP_ID = 104_000_000;
    private static final int UPPER_SHIP_EXIT_PORTAL_ID = 31;
    private static final int LOWER_SHIP_EXIT_PORTAL_ID = 20;
    private static final int LOWER_LEFT_ENTRY_PORTAL_ID = 30;
    private static final int TOWN_SIDE_MAX_X = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.TOWN_SIDE_MAX_X");
    private static final int LOWER_LEFT_EXIT_MAX_X = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.LOWER_LEFT_EXIT_MAX_X");
    private static final int UPPER_SHIP_MAX_X = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.UPPER_SHIP_MAX_X");
    private static final int PORTAL_DISTANCE_PX = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.PORTAL_DISTANCE_PX");
    private static final int DESCENT_FALLBACK_STUCK_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.DESCENT_FALLBACK_STUCK_MS");
    private static final Point SAFE_SHIP_FALLBACK = new Point(4_188, -224);
    private static final List<ShipPlatform> SHIP_ARRIVAL_PLATFORMS = List.of(
            new ShipPlatform(4_050, 4_325, -223));
    /*
     * Authored from Lith Harbor's footholds. Each lane describes a supported landing surface,
     * not a behavior-tuning constant. A stable per-Agent x within the lane avoids both portal
     * convergence and repeated random replanning while the Agent crosses the upper ship deck.
     */
    private static final List<DescentLane> UPPER_SHIP_DESCENT_LANES = List.of(
            new DescentLane(2_770, 2_810, 107),
            new DescentLane(2_855, 2_905, 47),
            new DescentLane(2_950, 3_070, 107),
            new DescentLane(3_040, 3_080, 47),
            new DescentLane(3_270, 3_350, 424),
            new DescentLane(3_390, 3_490, 453),
            new DescentLane(3_550, 3_600, 452),
            new DescentLane(3_670, 3_790, 227),
            new DescentLane(3_842, 3_858, 428),
            new DescentLane(3_990, 4_090, 437),
            new DescentLane(4_110, 4_250, 437),
            new DescentLane(4_290, 4_430, 437),
            new DescentLane(4_470, 4_610, 437),
            new DescentLane(4_650, 4_790, 437));

    private AgentLithHarborArrivalRouteRuntime() {
    }

    public enum TravelProgress {
        ARRIVED,
        YIELD_TO_MOVEMENT,
        ACTION_CONSUMED
    }

    /**
     * Selects a varied Victoria test arrival on one of the navigable ship platforms.
     */
    public static Point victoriaArrivalPosition(MapleMap map, int selector) {
        return shipArrivalPosition(map, selector);
    }

    /** Stages every Shanks arrival on the same navigable ship platform used by Victoria tests. */
    public static void stageAfterShanks(AgentRuntimeEntry entry, Character agent, int selector) {
        if (entry == null || agent == null || agent.getMapId() != LITH_HARBOR_MAP_ID
                || agent.getMap() == null) {
            return;
        }
        Point position = shipArrivalPosition(agent.getMap(), selector);
        AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .stagePosition(entry, agent, position);
        prepareNavigation(entry, agent);
    }

    /**
     * Advances the isolated-ship arrival route without hiding whether the caller must yield the
     * remainder of the tick to ordinary movement/physics.
     */
    public static TravelProgress advanceToTown(AgentRuntimeEntry entry,
                                               Character agent,
                                               PrimitiveCapabilityGateway gateway) {
        prepareNavigation(entry, agent);
        if (!gateway.grounded(agent)) {
            return TravelProgress.YIELD_TO_MOVEMENT;
        }
        Point descentTarget = upperShipDescentTarget(agent);
        if (descentTarget != null && gateway.stuckDurationMs(entry) < DESCENT_FALLBACK_STUCK_MS) {
            gateway.navigate(entry, descentTarget, true);
            return TravelProgress.YIELD_TO_MOVEMENT;
        }
        Integer portalId = nextPortalId(agent);
        if (portalId == null) {
            return TravelProgress.ARRIVED;
        }
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) {
            return TravelProgress.ACTION_CONSUMED;
        }
        if (agent.getPosition().distanceSq(portal) <= PORTAL_DISTANCE_PX * PORTAL_DISTANCE_PX) {
            gateway.enterPortal(agent, portalId);
            return TravelProgress.ACTION_CONSUMED;
        }
        gateway.navigate(entry, portal, true);
        return TravelProgress.YIELD_TO_MOVEMENT;
    }

    /** Returns true while an Agent is still on the isolated ship side of Lith Harbor. */
    public static boolean travelToTown(AgentRuntimeEntry entry,
                                       Character agent,
                                       PrimitiveCapabilityGateway gateway) {
        return advanceToTown(entry, agent, gateway) != TravelProgress.ARRIVED;
    }

    static Integer nextPortalId(Character agent) {
        if (agent == null || agent.getMapId() != LITH_HARBOR_MAP_ID) {
            return null;
        }
        Point position = agent.getPosition();
        if (position.x < 0) {
            return LOWER_LEFT_ENTRY_PORTAL_ID;
        }
        if (position.x <= TOWN_SIDE_MAX_X
                || (position.y >= 400 && position.x <= LOWER_LEFT_EXIT_MAX_X)) {
            return null;
        }
        return position.y < 0 && position.x < UPPER_SHIP_MAX_X
                ? UPPER_SHIP_EXIT_PORTAL_ID
                : LOWER_SHIP_EXIT_PORTAL_ID;
    }

    private static Point upperShipDescentTarget(Character agent) {
        if (agent == null || agent.getMapId() != LITH_HARBOR_MAP_ID) {
            return null;
        }
        Point position = agent.getPosition();
        if (position == null || position.y >= 0
                || position.x <= TOWN_SIDE_MAX_X || position.x >= UPPER_SHIP_MAX_X) {
            return null;
        }
        return descentWaypoint(agent.getId());
    }

    static Point descentWaypoint(int selector) {
        int mixed = mix(selector);
        return descentWaypointForLane(
                Math.floorMod(mixed, UPPER_SHIP_DESCENT_LANES.size()), mixed);
    }

    static Point descentWaypointForLane(int laneIndex, int selector) {
        DescentLane lane = UPPER_SHIP_DESCENT_LANES.get(
                Math.floorMod(laneIndex, UPPER_SHIP_DESCENT_LANES.size()));
        int x = lane.minX() + Math.floorMod(mix(selector), lane.width());
        return new Point(x, lane.y());
    }

    static int descentLaneCount() {
        return UPPER_SHIP_DESCENT_LANES.size();
    }

    private static Point shipArrivalPosition(MapleMap map, int selector) {
        Point candidate = selectShipSpawn(selector);
        Point ground = ground(map, candidate);
        if (ground != null) {
            return ground;
        }
        Portal arrival = map.getPortal("in03");
        return arrival != null ? new Point(arrival.getPosition()) : new Point(SAFE_SHIP_FALLBACK);
    }

    private static Point ground(MapleMap map, Point candidate) {
        return AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
    }

    private static Point selectShipSpawn(int selector) {
        int totalWidth = SHIP_ARRIVAL_PLATFORMS.stream().mapToInt(ShipPlatform::width).sum();
        int offset = Math.floorMod(selector, totalWidth);
        for (ShipPlatform platform : SHIP_ARRIVAL_PLATFORMS) {
            if (offset < platform.width()) {
                return new Point(platform.minX() + offset, platform.y());
            }
            offset -= platform.width();
        }
        throw new IllegalStateException("Lith Harbor ship platform catalog is empty");
    }

    public static void prepareNavigation(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return;
        }
        AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, agent);
    }

    private record ShipPlatform(int minX, int maxX, int y) {
        private int width() {
            return maxX - minX + 1;
        }
    }

    private record DescentLane(int minX, int maxX, int y) {
        private int width() {
            return maxX - minX + 1;
        }
    }

    /** Small deterministic integer mixer; identity-derived choices remain stable across ticks. */
    private static int mix(int value) {
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }
}
