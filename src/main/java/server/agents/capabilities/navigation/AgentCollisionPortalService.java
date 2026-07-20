package server.agents.capabilities.navigation;

import client.Character;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;

/** Emulates the v83 client's intent-independent collision-portal check for headless Agents. */
public final class AgentCollisionPortalService {
    private static final int NO_DESTINATION_MAP_ID = 999_999_999;

    private AgentCollisionPortalService() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent) {
        return tick(entry, agent, AgentMapGatewayRuntime.map());
    }

    static boolean tick(AgentRuntimeEntry entry, Character agent, MapGateway maps) {
        if (entry == null || agent == null || maps == null) {
            return false;
        }
        MapleMap map = agent.getMap();
        Point position = agent.getPosition();
        if (map == null || position == null) {
            return false;
        }
        for (Portal portal : map.getPortals()) {
            if (!isAutomaticTouchWarp(portal) || !portal.getPortalStatus()) {
                continue;
            }
            Point portalPosition = portal.getPosition();
            if (portalPosition != null
                    && Math.abs(position.x - portalPosition.x) <= AgentPortalApproachService.COLLISION_ENTER_X
                    && Math.abs(position.y - portalPosition.y) <= AgentPortalApproachService.COLLISION_ENTER_Y
                    && AgentNavigationPortalService.tryExecutePortal(entry, agent, portal.getId(), maps)) {
                return true;
            }
        }
        return false;
    }

    static boolean isAutomaticTouchWarp(Portal portal) {
        if (portal == null) {
            return false;
        }
        String script = portal.getScriptName();
        boolean hasScript = script != null && !script.isEmpty();
        return portal.getType() == AgentPortalApproachService.SCRIPTED_COLLISION_PORTAL_TYPE
                ? hasScript
                : portal.getType() == AgentPortalApproachService.COLLISION_PORTAL_TYPE
                && portal.getTargetMapId() != NO_DESTINATION_MAP_ID
                && !hasScript;
    }
}
