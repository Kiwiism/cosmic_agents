package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.Comparator;
import java.util.concurrent.ScheduledFuture;

/** Independent lease watchdog so a KPQ cannot depend on one Agent tick for cleanup or progress. */
final class AgentKpqWatchdogRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqWatchdogRuntime.class);
    private static ScheduledFuture<?> task;

    private AgentKpqWatchdogRuntime() {
    }

    static synchronized void ensureStarted() {
        if (task != null) return;
        long periodMs = Math.max(250L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqWatchdogRuntime.PERIOD_MS"));
        task = AgentSchedulerRuntime.register(AgentKpqWatchdogRuntime::tickSafely, periodMs);
    }

    private static void tickSafely() {
        long nowMs = System.currentTimeMillis();
        for (AgentKpqSession session : AgentKpqSessionRegistry.sessions()) {
            try {
                tick(session, nowMs);
            } catch (RuntimeException failure) {
                log.warn("KPQ watchdog failed for session {}", session.sessionId(), failure);
            }
        }
    }

    static void tick(AgentKpqSession session, long nowMs) {
        if (session == null || session.paused()
                || session.phase() == AgentKpqSession.Phase.COMPLETED
                || session.phase() == AgentKpqSession.Phase.FAILED) {
            return;
        }
        AgentRuntimeEntry candidateEntry = session.members().stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .map(member -> AgentRuntimeRegistry.findByAgentCharacterId(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(entry -> {
                    Character agent = AgentRuntimeIdentityRuntime.bot(entry);
                    return agent != null && agent.getHp() > 0;
                })
                .findFirst().orElse(null);
        if (candidateEntry == null) {
            AgentKpqTerminationService.fail(
                    session, "No live Agent coordinator remains", nowMs);
            return;
        }
        Character candidate = AgentRuntimeIdentityRuntime.bot(candidateEntry);
        long leaseMs = config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqRuntime.COORDINATOR_LEASE_MS");
        if (session.claimExpiredCoordinatorTick(candidate.getId(), nowMs, leaseMs)) {
            AgentKpqCoordinator.tick(session, nowMs);
        }
    }
}
