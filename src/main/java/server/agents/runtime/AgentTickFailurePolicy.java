package server.agents.runtime;

import client.Character;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentTickFailureStateRuntime;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Agent-owned policy for per-agent tick failure counting and escalation.
 */
public final class AgentTickFailurePolicy {
    public static final int FAILURE_LIMIT = 3;
    public static final long FAILURE_WINDOW_MS = 10_000L;

    public record Decision(int failureCount, boolean forceIdle, boolean disableAgent) {
    }

    public record FailureContext(String agentName,
                                 String leaderName,
                                 int mapId,
                                 int failureCount,
                                 boolean grinding,
                                 boolean following,
                                 Throwable failure) {
    }

    public record FailureHooks(MissingEntryLogger missingEntryLogger,
                               BiConsumer<AgentRuntimeEntry, Throwable> clearMovementState,
                               Consumer<AgentRuntimeEntry> disableAgent,
                               Consumer<AgentRuntimeEntry> forceIdle,
                               BiConsumer<FailureContext, Throwable> logDisable,
                               BiConsumer<FailureContext, Throwable> logWarning) {
    }

    @FunctionalInterface
    public interface MissingEntryLogger {
        void log(int leaderCharId, int agentCharId, Throwable failure);
    }

    private AgentTickFailurePolicy() {
    }

    public static void handleFailure(AgentRuntimeEntry entry,
                                     int leaderCharId,
                                     int agentCharId,
                                     Throwable failure,
                                     long nowMs,
                                     FailureHooks hooks) {
        if (entry == null) {
            hooks.missingEntryLogger().log(leaderCharId, agentCharId, failure);
            return;
        }

        Decision decision = recordFailure(entry, nowMs);
        FailureContext context = failureContext(entry, decision.failureCount(), failure);

        clearVolatileActions(entry);
        hooks.clearMovementState().accept(entry, failure);
        if (decision.disableAgent()) {
            hooks.logDisable().accept(context, failure);
            hooks.disableAgent().accept(entry);
            return;
        }

        if (decision.forceIdle()) {
            hooks.forceIdle().accept(entry);
        }

        hooks.logWarning().accept(context, failure);
    }

    public static Decision recordFailure(AgentRuntimeEntry entry, long nowMs) {
        int failureCount = AgentTickFailureStateRuntime.recordFailure(entry, nowMs, FAILURE_WINDOW_MS);
        return new Decision(failureCount, failureCount == 2, failureCount >= FAILURE_LIMIT);
    }

    public static void resetFailures(AgentRuntimeEntry entry) {
        if (entry != null && AgentTickFailureStateRuntime.hasFailures(entry)) {
            AgentTickFailureStateRuntime.clear(entry);
        }
    }

    public static void clearVolatileActions(AgentRuntimeEntry entry) {
        AgentPendingActionStateRuntime.clearPendingAction(entry);
        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentGrindTargetStateRuntime.clear(entry);
        AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentPatrolStateRuntime.clearPatrolWanderTarget(entry);
    }

    private static FailureContext failureContext(AgentRuntimeEntry entry, int failureCount, Throwable failure) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        return new FailureContext(
                agent != null ? agent.getName() : "?",
                leader != null ? leader.getName() : "?",
                agent != null ? agent.getMapId() : -1,
                failureCount,
                AgentModeStateRuntime.grinding(entry),
                AgentModeStateRuntime.following(entry),
                failure);
    }
}
