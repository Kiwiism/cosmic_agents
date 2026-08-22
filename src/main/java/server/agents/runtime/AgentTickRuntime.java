package server.agents.runtime;

import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.runtime.activity.AgentActivityHostState;
import server.agents.runtime.simulation.AgentAbstractTickRuntime;
import server.agents.runtime.simulation.AgentSimulationMode;

import java.util.function.Consumer;

public final class AgentTickRuntime {
    private AgentTickRuntime() {
    }

    public static void tick(AgentRuntimeEntry entry,
                            int leaderCharId,
                            int agentCharId,
                            Consumer<AgentRuntimeEntry> issueGrind,
                            Consumer<AgentRuntimeEntry> issueFollow) {
        long nowMs = System.currentTimeMillis();
        AgentBehaviorRuntime.adaptation(entry).observe(entry.capabilityStates()
                .find(AgentActivityHostState.STATE_KEY)
                .map(AgentActivityHostState::activityKind).orElse(null), nowMs);
        if (entry.simulationState().mode() == AgentSimulationMode.BACKGROUND_ABSTRACT) {
            entry.tickSliceState().clear();
            try {
                AgentAbstractTickRuntime.tick(entry, nowMs);
                AgentTickFailurePolicy.resetFailures(entry);
            } catch (Throwable failure) {
                AgentTickFailureRuntime.handleFailure(
                        entry, leaderCharId, agentCharId, failure);
            }
            return;
        }
        AgentTickSliceState tickSliceState = entry.tickSliceState();
        if (tickSliceState != null && tickSliceState.enabled()) {
            AgentTickSliceRuntime.tick(
                    entry,
                    leaderCharId,
                    agentCharId,
                    issueGrind,
                    issueFollow);
            return;
        }
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
