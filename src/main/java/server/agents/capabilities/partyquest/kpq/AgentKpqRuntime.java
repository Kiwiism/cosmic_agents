package server.agents.capabilities.partyquest.kpq;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Activity-controller facade for the isolated KPQ aggregate. */
public final class AgentKpqRuntime {
    private AgentKpqRuntime() {
    }

    public static boolean active(int characterId) {
        return AgentKpqSessionRegistry.active(characterId);
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(agent.getId());
        if (session == null || session.paused()) return session != null;
        if (!session.claimCoordinatorTick(agent.getId(), nowMs)) return true;
        AgentKpqCoordinator.tick(session, nowMs);
        return true;
    }

    public static boolean requestStop(int characterId, String reason, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session == null) return true;
        boolean safe = session.phase() == AgentKpqSession.Phase.WAITING_OUTSIDE_TEST
                || session.phase() == AgentKpqSession.Phase.COMPLETED
                || session.phase() == AgentKpqSession.Phase.FAILED;
        if (safe) {
            session.members().forEach(member -> {
                AgentRuntimeEntry entry = server.agents.runtime.AgentRuntimeRegistry
                        .findByAgentCharacterId(member.characterId());
                if (entry != null) AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
            });
            AgentKpqSessionRegistry.remove(session);
        }
        return safe;
    }

    public static void forceStop(int characterId, String reason, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session == null) return;
        session.fail(reason, nowMs);
        session.members().forEach(member -> {
            AgentRuntimeEntry entry = server.agents.runtime.AgentRuntimeRegistry
                    .findByAgentCharacterId(member.characterId());
            if (entry != null) AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        });
        AgentKpqSessionRegistry.remove(session);
    }
}
