package server.agents.runtime;

import java.util.function.Consumer;

public final class AgentTickRuntime {
    private AgentTickRuntime() {
    }

    public static void tick(AgentRuntimeEntry entry,
                            int leaderCharId,
                            int agentCharId,
                            Consumer<AgentRuntimeEntry> issueGrind,
                            Consumer<AgentRuntimeEntry> issueFollow) {
        AgentTickOrchestrator.runGuardedTick(
                entry,
                leaderCharId,
                agentCharId,
                (tickEntry, tickLeaderId, tickAgentId) -> AgentTickCoreRuntime.tickCore(
                        tickEntry,
                        tickLeaderId,
                        tickAgentId,
                        issueGrind,
                        issueFollow),
                (failedEntry, failedLeaderId, failedAgentId, failure) -> AgentTickFailureRuntime.handleFailure(
                        failedEntry,
                        failedLeaderId,
                        failedAgentId,
                        failure));
    }
}
