package server.agents.capabilities.partyquest.opq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** OPQ facade: central coordination lease plus independent per-member room work. */
public final class AgentOpqRuntime {
    private static final long COORDINATOR_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.opq.AgentOpqRuntime.COORDINATOR_LEASE_MS");
    private AgentOpqRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentOpqSession session = AgentOpqSessionRegistry.forMember(agent.getId());
        if (session == null) return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        if (session.paused()) return true;
        AgentOpqCoordinator.tickMember(session, entry, agent, nowMs);
        if (session.claimExecutionTick(agent.getId(), nowMs, COORDINATOR_LEASE_MS)) {
            AgentOpqCoordinator.tickSession(session, nowMs);
        }
        return true;
    }

    public static boolean requestStop(int id, String reason, long nowMs) {
        AgentOpqSession session = AgentOpqSessionRegistry.forMember(id);
        if (session == null) return AgentPartyQuestLifecycleRuntime.requestStop(id, reason, nowMs);
        if (!session.terminal()) return false;
        AgentOpqTerminationService.release(session, reason, nowMs, false);
        return true;
    }

    public static void forceStop(int id, String reason, long nowMs) {
        AgentOpqSession session = AgentOpqSessionRegistry.forMember(id);
        if (session == null) AgentPartyQuestLifecycleRuntime.forceStop(id, reason, nowMs);
        else AgentOpqTerminationService.release(session, reason, nowMs, true);
    }

    public static void runtimeRemoved(int id, long nowMs) {
        forceStop(id, "Agent runtime removed", nowMs);
    }
}
