package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;

/** Activity-controller facade for the isolated KPQ aggregate. */
public final class AgentKpqRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqRuntime.class);
    private static final long COORDINATOR_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqRuntime.COORDINATOR_LEASE_MS");
    private AgentKpqRuntime() {
    }

    public static boolean active(int characterId) {
        return AgentKpqSessionRegistry.active(characterId)
                || AgentPartyQuestLifecycleRuntime.active(characterId);
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(agent.getId());
        if (session == null) return AgentPartyQuestLifecycleRuntime.tick(agent.getId(), nowMs);
        if (session.paused()) return true;
        int previousCoordinator = session.coordinatorAgentId();
        if (!session.claimCoordinatorTick(agent.getId(), nowMs, COORDINATOR_LEASE_MS)) return true;
        if (previousCoordinator != session.coordinatorAgentId()) {
            log.warn("KPQ execution lease transferred without changing the party caller: session={} from={} to={} caller={}",
                    session.sessionId(), previousCoordinator, session.coordinatorAgentId(),
                    session.formationCallerId());
        }
        AgentKpqCoordinator.tick(session, nowMs);
        return true;
    }

    public static boolean requestStop(int characterId, String reason, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session == null) {
            return AgentPartyQuestLifecycleRuntime.requestStop(characterId, reason, nowMs);
        }
        boolean safe = session.phase() == AgentKpqSession.Phase.COMPLETED
                || session.phase() == AgentKpqSession.Phase.FAILED;
        if (safe) {
            if (session.phase() == AgentKpqSession.Phase.FAILED) {
                AgentKpqTerminationService.fail(session, reason, nowMs);
            } else {
                AgentKpqTerminationService.complete(session, nowMs);
            }
        }
        return safe;
    }

    public static void forceStop(int characterId, String reason, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session == null) AgentPartyQuestLifecycleRuntime.forceStop(characterId, reason, nowMs);
        else AgentKpqTerminationService.fail(session, reason, nowMs);
    }

    public static void runtimeRemoved(int characterId, long nowMs) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session != null) {
            AgentKpqTerminationService.fail(session, "Agent runtime was removed", nowMs);
        } else {
            AgentPartyQuestLifecycleRuntime.runtimeRemoved(
                    characterId, "Agent runtime was removed", nowMs);
        }
    }
}
