package server.agents.capabilities.partyquest.ppq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** PPQ facade over independent member work and one short coordinator lease. */
public final class AgentPpqRuntime {
    private static final long COORDINATOR_LEASE_MS = 3_000L;
    private AgentPpqRuntime() { }
    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentPpqSession session = AgentPpqSessionRegistry.forMember(agent.getId());
        if (session == null) {
            AgentPpqLobbyAdmissionRuntime.tick(agent.getId(), nowMs);
            return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        }
        if (session.paused()) return true;
        AgentPpqCoordinator.tickMember(session, entry, agent, nowMs);
        if (session.claimExecutionTick(agent.getId(), nowMs, COORDINATOR_LEASE_MS)) {
            AgentPpqCoordinator.tickSession(session, nowMs);
        }
        if (session.terminal()) AgentPpqTerminationService.release(
                session, session.failure(), nowMs, session.phase() == AgentPpqSession.Phase.FAILED);
        return true;
    }
    public static boolean requestStop(int id, String reason, long nowMs) {
        AgentPpqSession session = AgentPpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentPpqLobbyAdmissionRuntime.releaseTracking(id);
            return AgentPartyQuestLifecycleRuntime.requestStop(id, reason, nowMs);
        }
        if (!session.terminal()) return false;
        AgentPpqTerminationService.release(session, reason, nowMs, false); return true;
    }
    public static void forceStop(int id, String reason, long nowMs) {
        AgentPpqSession session = AgentPpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentPpqLobbyAdmissionRuntime.releaseTracking(id);
            AgentPartyQuestLifecycleRuntime.forceStop(id, reason, nowMs);
        } else AgentPpqTerminationService.release(session, reason, nowMs, true);
    }
    public static void runtimeRemoved(int id, long nowMs) { forceStop(id, "Agent runtime removed", nowMs); }
}
