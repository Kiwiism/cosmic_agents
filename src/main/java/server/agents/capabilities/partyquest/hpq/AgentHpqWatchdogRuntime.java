package server.agents.capabilities.partyquest.hpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.Comparator;
import java.util.concurrent.ScheduledFuture;

/** Independent HPQ lease watchdog for coordinator failover and orphan cleanup. */
final class AgentHpqWatchdogRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentHpqWatchdogRuntime.class);
    private static ScheduledFuture<?> task;

    private AgentHpqWatchdogRuntime() {
    }

    static synchronized void ensureStarted() {
        if (task != null) return;
        long periodMs = Math.max(250L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqWatchdogRuntime.PERIOD_MS"));
        task = AgentSchedulerRuntime.register(AgentHpqWatchdogRuntime::tickSafely, periodMs);
    }

    private static void tickSafely() {
        long nowMs = System.currentTimeMillis();
        for (AgentHpqSession session : AgentHpqSessionRegistry.sessions()) {
            try {
                tick(session, nowMs);
            } catch (RuntimeException failure) {
                log.warn("HPQ watchdog failed for session {}", session.sessionId(), failure);
            }
        }
    }

    static void tick(AgentHpqSession session, long nowMs) {
        if (session == null || session.paused() || session.terminal()) return;
        AgentRuntimeEntry candidateEntry = session.members().stream()
                .filter(member -> member.memberType() == AgentHpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(AgentHpqMemberState::characterId))
                .map(member -> AgentRuntimeRegistry.findByAgentCharacterId(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(entry -> {
                    Character agent = AgentRuntimeIdentityRuntime.bot(entry);
                    return agent != null && agent.getHp() > 0;
                })
                .findFirst().orElse(null);
        if (candidateEntry == null) {
            AgentHpqTerminationService.fail(
                    session, "No live Agent execution participant remains", nowMs);
            return;
        }
        Character candidate = AgentRuntimeIdentityRuntime.bot(candidateEntry);
        long leaseMs = config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqRuntime.COORDINATOR_LEASE_MS");
        if (session.claimExpiredExecutionTick(candidate.getId(), nowMs, leaseMs)) {
            AgentHpqCoordinator.tick(session, nowMs);
        }
    }
}
