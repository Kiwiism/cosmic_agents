package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.util.function.Consumer;

/**
 * Runtime hook bundle for tick failure side effects.
 */
public final class AgentTickFailureRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentTickFailureRuntime.class);

    private AgentTickFailureRuntime() {
    }

    public static void handleFailure(AgentRuntimeEntry entry,
                                     int leaderCharId,
                                     int agentCharId,
                                     Throwable failure) {
        handleFailure(
                entry,
                leaderCharId,
                agentCharId,
                failure,
                log,
                AgentBotMovementCommandRuntime::stop);
    }

    public static void handleFailure(AgentRuntimeEntry entry,
                                     int leaderCharId,
                                     int agentCharId,
                                     Throwable failure,
                                     Logger log,
                                     Consumer<AgentRuntimeEntry> stopAgent) {
        AgentTickFailurePolicy.handleFailure(
                entry,
                leaderCharId,
                agentCharId,
                failure,
                System.currentTimeMillis(),
                new AgentTickFailurePolicy.FailureHooks(
                        (missingLeaderCharId, missingAgentCharId, missingFailure) ->
                                log.error("Bot tick failed for missing entry ownerCharId={} botCharId={}",
                                        missingLeaderCharId, missingAgentCharId, missingFailure),
                        (failedEntry, ignored) -> AgentMovementStateResetService.resetEntryStateAfterTeleport(failedEntry),
                        failedEntry -> AgentRuntimeCleanupService.removeAgentByCharacterId(agentCharId),
                        failedEntry -> forceIdleAfterTickFailure(failedEntry, log, stopAgent),
                        (context, disableFailure) -> log.error(
                                "Disabling bot '{}' after {} tick failures within {} ms (owner={}, map={}, grinding={}, following={})",
                                context.agentName(), context.failureCount(), AgentTickFailurePolicy.FAILURE_WINDOW_MS,
                                context.leaderName(), context.mapId(), context.grinding(), context.following(), disableFailure),
                        (context, warningFailure) -> log.warn(
                                "Bot '{}' tick failed {}/{} (owner={}, map={}, grinding={}, following={})",
                                context.agentName(), context.failureCount(), AgentTickFailurePolicy.FAILURE_LIMIT,
                                context.leaderName(), context.mapId(), context.grinding(), context.following(), warningFailure)));
    }

    private static void forceIdleAfterTickFailure(AgentRuntimeEntry entry, Logger log, Consumer<AgentRuntimeEntry> stopAgent) {
        stopAgent.accept(entry);
        try {
            AgentReplyRuntime.replyNow(entry, "unrecoverable error caught, idling");
        } catch (Throwable chatError) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            log.warn("Failed to send bot failure idle message for '{}'",
                    agent != null ? agent.getName() : "?", chatError);
        }
    }

}
