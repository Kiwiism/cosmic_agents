package server.agents.capabilities.partyquest.hpq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Activity-controller facade for the isolated HPQ aggregate. */
public final class AgentHpqRuntime {
    private static final long COORDINATOR_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqRuntime.COORDINATOR_LEASE_MS");

    private AgentHpqRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentHpqSession session = AgentHpqSessionRegistry.forMember(agent.getId());
        if (session == null) {
            AgentHpqLobbyAdmissionRuntime.tick(agent.getId(), nowMs);
            return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        }
        if (session.paused()) return true;
        if (!session.claimExecutionTick(agent.getId(), nowMs, COORDINATOR_LEASE_MS)) return true;
        AgentHpqCoordinator.tick(session, nowMs);
        return true;
    }

    public static boolean requestStop(int characterId, String reason, long nowMs) {
        AgentHpqSession session = AgentHpqSessionRegistry.forMember(characterId);
        if (session == null) {
            AgentHpqLobbyAdmissionRuntime.releaseTracking(characterId);
            return AgentPartyQuestLifecycleRuntime.requestStop(characterId, reason, nowMs);
        }
        if (!session.terminal()) return false;
        if (session.phase() == AgentHpqSession.Phase.COMPLETED) {
            AgentHpqTerminationService.complete(session, nowMs);
        } else {
            AgentHpqTerminationService.fail(session, reason, nowMs);
        }
        return true;
    }

    public static void forceStop(int characterId, String reason, long nowMs) {
        AgentHpqSession session = AgentHpqSessionRegistry.forMember(characterId);
        if (session == null) {
            AgentHpqLobbyAdmissionRuntime.releaseTracking(characterId);
            AgentPartyQuestLifecycleRuntime.forceStop(characterId, reason, nowMs);
        } else {
            AgentHpqTerminationService.fail(session, reason, nowMs);
        }
    }

    public static void runtimeRemoved(int characterId, long nowMs) {
        forceStop(characterId, "Agent runtime was removed", nowMs);
    }
}
