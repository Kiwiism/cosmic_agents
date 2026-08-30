package server.agents.capabilities.partyquest.lpq;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.Comparator;
import java.util.concurrent.ScheduledFuture;

/** LPQ-only coordinator failover and orphan watchdog. */
final class AgentLpqWatchdogRuntime {
    private static ScheduledFuture<?> task;
    private AgentLpqWatchdogRuntime() { }
    static synchronized void ensureStarted() {
        if (task != null) return;
        long period = Math.max(250L, config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqWatchdogRuntime.PERIOD_MS"));
        task = AgentSchedulerRuntime.register(() -> {
            long now = System.currentTimeMillis();
            AgentLpqSessionRegistry.sessions().forEach(session -> tick(session, now));
        }, period);
    }
    static void tick(AgentLpqSession session, long nowMs) {
        if (session == null || session.paused() || session.terminal()) return;
        AgentLpqCoordinator.tickStageFourRoomRecoveryWatchdog(session, nowMs);
        AgentRuntimeEntry entry = session.members().stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(AgentLpqMemberState::characterId))
                .map(member -> AgentRuntimeRegistry.findByAgentCharacterId(member.characterId()))
                .filter(java.util.Objects::nonNull).filter(candidate -> {
                    Character agent = AgentRuntimeIdentityRuntime.bot(candidate);
                    return agent != null && agent.getHp() > 0;
                }).findFirst().orElse(null);
        if (entry == null) { AgentLpqTerminationService.fail(session, "No live Agent remains", nowMs); return; }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        long lease = config.AgentTuning.longValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqRuntime.COORDINATOR_LEASE_MS");
        if (session.claimExpiredExecutionTick(agent.getId(), nowMs, lease)) AgentLpqCoordinator.tick(session, nowMs);
    }
}
