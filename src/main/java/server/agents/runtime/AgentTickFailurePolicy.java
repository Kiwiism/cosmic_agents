package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotTickFailureStateRuntime;
import server.bots.BotEntry;

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
                               BiConsumer<BotEntry, Throwable> clearMovementState,
                               Consumer<BotEntry> disableAgent,
                               Consumer<BotEntry> forceIdle,
                               BiConsumer<FailureContext, Throwable> logDisable,
                               BiConsumer<FailureContext, Throwable> logWarning) {
    }

    @FunctionalInterface
    public interface MissingEntryLogger {
        void log(int leaderCharId, int agentCharId, Throwable failure);
    }

    private AgentTickFailurePolicy() {
    }

    public static void handleFailure(BotEntry entry,
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

    public static Decision recordFailure(BotEntry entry, long nowMs) {
        int failureCount = AgentBotTickFailureStateRuntime.recordFailure(entry, nowMs, FAILURE_WINDOW_MS);
        return new Decision(failureCount, failureCount == 2, failureCount >= FAILURE_LIMIT);
    }

    public static void resetFailures(BotEntry entry) {
        if (entry != null && AgentBotTickFailureStateRuntime.hasFailures(entry)) {
            AgentBotTickFailureStateRuntime.clear(entry);
        }
    }

    public static void clearVolatileActions(BotEntry entry) {
        AgentBotPendingActionStateRuntime.clearPendingAction(entry);
        AgentBotPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentBotPatrolStateRuntime.clearPatrolWanderTarget(entry);
    }

    private static FailureContext failureContext(BotEntry entry, int failureCount, Throwable failure) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character leader = AgentBotRuntimeIdentityRuntime.owner(entry);
        return new FailureContext(
                agent != null ? agent.getName() : "?",
                leader != null ? leader.getName() : "?",
                agent != null ? agent.getMapId() : -1,
                failureCount,
                AgentBotModeStateRuntime.grinding(entry),
                AgentBotModeStateRuntime.following(entry),
                failure);
    }
}
