package server.agents.capabilities.partyquest.lpq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Activity facade for the isolated LPQ aggregate. */
public final class AgentLpqRuntime {
    private static final long LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqRuntime.COORDINATOR_LEASE_MS");
    private AgentLpqRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentLpqSession session = AgentLpqSessionRegistry.forMember(agent.getId());
        if (session == null) {
            AgentLpqLobbyAdmissionRuntime.tick(agent.getId(), nowMs);
            return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        }
        if (session.paused()) return true;
        // A completed split-room occupant owns a local portal traversal, not a
        // party-wide decision. Advance it from that Agent's own runtime tick so
        // the route cannot stop when the last combat owner releases the central
        // coordinator lease.
        if (AgentLpqCoordinator.tickStageFourCompletedRoomExit(
                session, entry, agent, nowMs)) {
            return true;
        }
        if (AgentLpqCoordinator.tickStageFiveRoomContinuation(
                session, entry, agent, nowMs)) {
            return true;
        }
        if (!session.claimExecutionTick(agent.getId(), nowMs, LEASE_MS)) return true;
        AgentLpqCoordinator.tick(session, nowMs);
        return true;
    }

    public static boolean requestStop(int id, String reason, long nowMs) {
        AgentLpqSession session = AgentLpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentLpqLobbyAdmissionRuntime.releaseTracking(id);
            return AgentPartyQuestLifecycleRuntime.requestStop(id, reason, nowMs);
        }
        if (!session.terminal()) return false;
        if (session.phase() == AgentLpqSession.Phase.COMPLETED) AgentLpqTerminationService.complete(session, nowMs);
        else AgentLpqTerminationService.fail(session, reason, nowMs);
        return true;
    }

    public static void forceStop(int id, String reason, long nowMs) {
        AgentLpqSession session = AgentLpqSessionRegistry.forMember(id);
        if (session == null) {
            AgentLpqLobbyAdmissionRuntime.releaseTracking(id);
            AgentPartyQuestLifecycleRuntime.forceStop(id, reason, nowMs);
        }
        else AgentLpqTerminationService.fail(session, reason, nowMs);
    }

    public static void runtimeRemoved(int id, long nowMs) { forceStop(id, "Agent runtime removed", nowMs); }
}
