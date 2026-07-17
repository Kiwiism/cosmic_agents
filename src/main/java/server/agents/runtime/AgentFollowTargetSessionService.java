package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

/** Refreshes an optional follow target without coupling Agent lifecycle to it. */
public final class AgentFollowTargetSessionService {
    private AgentFollowTargetSessionService() {
    }

    public static Character resolveLiveFollowTarget(AgentRuntimeEntry entry) {
        Character target = AgentRelationshipRuntime.followTarget(entry);
        if (target == null) {
            return AgentRuntimeIdentityRuntime.bot(entry);
        }
        if (target.isLoggedinWorld()) {
            return target;
        }

        Character refreshed = AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(target.getId());
        if (refreshed != null && refreshed.isLoggedinWorld()) {
            AgentRelationshipRuntime.setFollowTarget(entry, refreshed);
            return refreshed;
        }

        AgentRelationshipRuntime.setFollowTarget(entry, null);
        AgentModeStateRuntime.stopFollowing(entry);
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
