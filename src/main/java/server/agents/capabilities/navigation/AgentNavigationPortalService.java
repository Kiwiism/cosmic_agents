package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Portal;

import java.awt.Point;

/**
 * Agent-owned portal edge execution for navigation.
 */
public final class AgentNavigationPortalService {
    private static final long PORTAL_USE_COOLDOWN_MS = 250L;

    private AgentNavigationPortalService() {
    }

    public static boolean tryExecutePortal(AgentRuntimeEntry entry, Character agent, int portalId) {
        if (AgentBotNavigationDebugStateRuntime.portalUseOnCooldown(entry, System.currentTimeMillis())) {
            return false;
        }
        if (!usePortal(agent, portalId)) {
            return false;
        }

        AgentBotNavigationDebugStateRuntime.setPortalUseCooldownUntilMs(
                entry, System.currentTimeMillis() + PORTAL_USE_COOLDOWN_MS);
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentMovementStateResetService.resetEntryState(entry);
        return true;
    }

    private static boolean usePortal(Character agent, int portalId) {
        Portal portal = agent.getMap().getPortal(portalId);
        if (portal == null || !portal.getPortalStatus()) {
            return false;
        }

        int oldMapId = agent.getMapId();
        Point oldPos = agent.getPosition();
        portal.enterPortal(agent.getClient());
        return agent.getMapId() != oldMapId || !agent.getPosition().equals(oldPos);
    }
}
