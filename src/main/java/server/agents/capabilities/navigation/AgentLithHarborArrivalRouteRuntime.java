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
 * <p>Shanks owns the Agent's real arrival placement. This policy warms navigation at that exact
 * position, walks toward a reachable ship exit, and uses hidden portals only as the authored
 * connection into town. Synthetic Victoria resets may separately ask for a stable ship point
 * before the character is exposed to observers.</p>
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
    private static final int MAPLE_ISLAND_SHIP_MAX_X = 720;
    private static final String MAPLE_ISLAND_ARRIVAL_PORTAL = "maple00";
    private static final Point SAFE_SHIP_FALLBACK = new Point(84, 27);
    private static final List<ShipPlatform> SHIP_ARRIVAL_PLATFORMS = List.of(
            new ShipPlatform(40, 145, 27));
    private AgentLithHarborArrivalRouteRuntime() {
    }

    public enum TravelProgress {
        ARRIVED,
        YIELD_TO_MOVEMENT,
        ACTION_CONSUMED
    }

    /** Selects a stable, varied point on the Maple Island arrival ship deck. */
    public static Point victoriaArrivalPosition(MapleMap map, int selector) {
        return shipArrivalPosition(map, selector);
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
        if (position.x <= MAPLE_ISLAND_SHIP_MAX_X) {
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
        Point ground = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .groundPoint(map, candidate);
        if (ground != null) {
            return ground;
        }
        Portal arrival = map.getPortal(MAPLE_ISLAND_ARRIVAL_PORTAL);
        return arrival != null
                ? new Point(arrival.getPosition()) : new Point(SAFE_SHIP_FALLBACK);
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
