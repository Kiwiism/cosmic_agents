package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned portal edge execution for navigation.
 */
public final class AgentNavigationPortalService {
    private static final long PORTAL_USE_COOLDOWN_MS = config.AgentTuning.longValue("server.agents.capabilities.navigation.AgentNavigationPortalService.PORTAL_USE_COOLDOWN_MS");

    private AgentNavigationPortalService() {
    }

    public static boolean tryExecutePortal(AgentRuntimeEntry entry, Character agent, int portalId) {
        return tryExecutePortal(entry, agent, portalId, AgentMapGatewayRuntime.map());
    }

    public static boolean tryExecutePortal(AgentRuntimeEntry entry, Character agent, int portalId, MapGateway maps) {
        if (AgentNavigationDebugStateRuntime.portalUseOnCooldown(entry, System.currentTimeMillis())) {
            return false;
        }
        if (!maps.enterPortal(agent, portalId)) {
            return false;
        }

        AgentNavigationDebugStateRuntime.setPortalUseCooldownUntilMs(
                entry, System.currentTimeMillis() + PORTAL_USE_COOLDOWN_MS);
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentMovementStateResetService.resetEntryState(entry);
        return true;
    }
}
