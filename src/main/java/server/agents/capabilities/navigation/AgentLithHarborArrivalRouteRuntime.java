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
    private static final int DIRECT_WALK_MIN_X = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.DIRECT_WALK_MIN_X");
    private static final int DIRECT_WALK_MAX_X = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.DIRECT_WALK_MAX_X");
    private static final int DIRECT_WALK_Y = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime.DIRECT_WALK_Y");
    private static final Point SAFE_SHIP_FALLBACK = new Point(4_188, -224);
    private static final Point SAFE_LOWER_LEFT_FALLBACK = new Point(-572, 191);
    private static final List<ShipPlatform> SHIP_PLATFORMS = List.of(
            new ShipPlatform(2_800, 4_700, -223),
            new ShipPlatform(4_850, 5_300, -313),
            new ShipPlatform(4_710, 4_825, 77),
            new ShipPlatform(4_710, 5_080, 137),
            new ShipPlatform(4_800, 5_000, 257),
            new ShipPlatform(4_710, 5_080, 317),
            new ShipPlatform(4_000, 4_950, 437),
            new ShipPlatform(3_500, 4_850, 527));

    private AgentLithHarborArrivalRouteRuntime() {
    }

    /**
     * Selects a varied Victoria test arrival on one of the navigable ship platforms.
     */
    public static Point victoriaArrivalPosition(MapleMap map, int selector) {
        return shipArrivalPosition(map, selector);
    }

    /**
     * Moves an Agent arriving through Shanks away from portal 0 before its next movement tick.
     * Shanks arrivals alternate between the lower-left hidden warp and a direct town walk.
     */
    public static void stageAfterShanks(AgentRuntimeEntry entry, Character agent, int selector) {
        if (entry == null || agent == null || agent.getMapId() != LITH_HARBOR_MAP_ID
                || agent.getMap() == null) {
            return;
        }
        Point position = Math.floorMod(selector, 2) == 0
                ? lowerLeftArrivalPosition(agent.getMap())
                : directWalkArrivalPosition(agent.getMap(), selector);
        AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .stagePosition(entry, agent, position);
        prepareNavigation(entry, agent);
    }

    /** Returns true while an Agent is still on the isolated ship side of Lith Harbor. */
    public static boolean travelToTown(AgentRuntimeEntry entry,
                                       Character agent,
                                       PrimitiveCapabilityGateway gateway) {
        prepareNavigation(entry, agent);
        Integer portalId = nextPortalId(agent);
        if (portalId == null) {
            return false;
        }
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) {
            return true;
        }
        if (agent.getPosition().distanceSq(portal) <= PORTAL_DISTANCE_PX * PORTAL_DISTANCE_PX) {
            gateway.enterPortal(agent, portalId);
        } else {
            gateway.navigate(entry, portal, true);
        }
        return true;
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

    private static Point shipArrivalPosition(MapleMap map, int selector) {
        Point candidate = selectShipSpawn(selector);
        Point ground = ground(map, candidate);
        if (ground != null) {
            return ground;
        }
        Portal arrival = map.getPortal("in03");
        return arrival != null ? new Point(arrival.getPosition()) : new Point(SAFE_SHIP_FALLBACK);
    }

    private static Point lowerLeftArrivalPosition(MapleMap map) {
        Portal portal = map.getPortal(LOWER_LEFT_ENTRY_PORTAL_ID);
        return portal != null
                ? new Point(portal.getPosition())
                : new Point(SAFE_LOWER_LEFT_FALLBACK);
    }

    private static Point directWalkArrivalPosition(MapleMap map, int selector) {
        int width = DIRECT_WALK_MAX_X - DIRECT_WALK_MIN_X + 1;
        Point candidate = new Point(
                DIRECT_WALK_MIN_X + Math.floorMod(selector, width),
                DIRECT_WALK_Y);
        Point ground = ground(map, candidate);
        return ground != null ? ground : candidate;
    }

    private static Point ground(MapleMap map, Point candidate) {
        return AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
    }

    private static Point selectShipSpawn(int selector) {
        int totalWidth = SHIP_PLATFORMS.stream().mapToInt(ShipPlatform::width).sum();
        int offset = Math.floorMod(selector, totalWidth);
        for (ShipPlatform platform : SHIP_PLATFORMS) {
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
}
