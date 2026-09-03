package server.agents.capabilities.partyquest.lmpq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** LMPQ facade over independent member work and one short coordinator lease. */
public final class AgentLmpqRuntime {
    private static final long COORDINATOR_LEASE_MS = 3_000L;
    private AgentLmpqRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentLmpqSession session = AgentLmpqSessionRegistry.forMember(agent.getId());
        if (session == null) {
            AgentLmpqLobbyAdmissionRuntime.tick(agent.getId(), nowMs);
            return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        }
        if (session.paused()) return true;
        AgentLmpqCoordinator.tickMember(session, entry, agent, nowMs);
        if (session.claimExecutionTick(agent.getId(), nowMs, COORDINATOR_LEASE_MS)) {
            AgentLmpqCoordinator.tickSession(session, nowMs);
        }
        if (session.terminal()) AgentLmpqTerminationService.release(
                session, session.failure(), nowMs, session.phase() == AgentLmpqSession.Phase.FAILED);
        return true;
    }

    public static boolean requestStop(int id, String reason, long nowMs) {
        AgentLmpqSession session = AgentLmpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentLmpqLobbyAdmissionRuntime.releaseTracking(id);
            return AgentPartyQuestLifecycleRuntime.requestStop(id, reason, nowMs);
        }
        if (!session.terminal()) return false;
        AgentLmpqTerminationService.release(session, reason, nowMs, false);
        return true;
    }

    public static void forceStop(int id, String reason, long nowMs) {
        AgentLmpqSession session = AgentLmpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentLmpqLobbyAdmissionRuntime.releaseTracking(id);
            AgentPartyQuestLifecycleRuntime.forceStop(id, reason, nowMs);
        } else AgentLmpqTerminationService.release(session, reason, nowMs, true);
    }

    public static void runtimeRemoved(int id, long nowMs) {
        forceStop(id, "Agent runtime removed", nowMs);
    }
}
