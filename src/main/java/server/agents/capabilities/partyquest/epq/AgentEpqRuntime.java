package server.agents.capabilities.partyquest.epq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** EPQ facade: independent member work plus one short party coordination lease. */
public final class AgentEpqRuntime {
    private static final long COORDINATOR_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqRuntime.COORDINATOR_LEASE_MS");
    private AgentEpqRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentEpqSession session = AgentEpqSessionRegistry.forMember(agent.getId());
        if (session == null) {
            AgentEpqLobbyAdmissionRuntime.tick(agent.getId(), nowMs);
            return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        }
        if (session.paused()) return true;
        AgentEpqCoordinator.tickMember(session, entry, agent, nowMs);
        if (session.claimExecutionTick(agent.getId(), nowMs, COORDINATOR_LEASE_MS)) {
            AgentEpqCoordinator.tickSession(session, nowMs);
        }
        if (session.terminal()) AgentEpqTerminationService.release(
                session, session.failure(), nowMs,
                session.phase() == AgentEpqSession.Phase.FAILED);
        return true;
    }

    public static boolean requestStop(int id, String reason, long nowMs) {
        AgentEpqSession session = AgentEpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentEpqLobbyAdmissionRuntime.releaseTracking(id);
            return AgentPartyQuestLifecycleRuntime.requestStop(id, reason, nowMs);
        }
        if (!session.terminal()) return false;
        AgentEpqTerminationService.release(session, reason, nowMs, false);
        return true;
    }

    public static void forceStop(int id, String reason, long nowMs) {
        AgentEpqSession session = AgentEpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentEpqLobbyAdmissionRuntime.releaseTracking(id);
            AgentPartyQuestLifecycleRuntime.forceStop(id, reason, nowMs);
        }
        else AgentEpqTerminationService.release(session, reason, nowMs, true);
    }

    public static void runtimeRemoved(int id, long nowMs) {
        forceStop(id, "Agent runtime removed", nowMs);
    }
}
