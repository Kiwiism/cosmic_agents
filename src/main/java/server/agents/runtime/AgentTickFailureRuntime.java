package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.util.function.Consumer;

/**
 * Temporary runtime hook bundle for tick failure side effects while BotManager
 * still owns the guarded tick entry point.
 */
public final class AgentTickFailureRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentTickFailureRuntime.class);

    private AgentTickFailureRuntime() {
    }

    public static void handleFailure(BotEntry entry,
                                     int leaderCharId,
                                     int agentCharId,
                                     Throwable failure) {
        handleFailure(entry, leaderCharId, agentCharId, failure, log, AgentBotMovementCommandRuntime::stop);
    }

    public static void handleFailure(BotEntry entry,
                                     int leaderCharId,
                                     int agentCharId,
                                     Throwable failure,
                                     Logger log,
                                     Consumer<BotEntry> stopAgent) {
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
                        (failedEntry, ignored) -> BotMovementManager.resetEntryStateAfterTeleport(failedEntry),
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

    private static void forceIdleAfterTickFailure(BotEntry entry, Logger log, Consumer<BotEntry> stopAgent) {
        stopAgent.accept(entry);
        try {
            AgentBotManagerReplyRuntime.replyNow(entry, "unrecoverable error caught, idling");
        } catch (Throwable chatError) {
            Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
            log.warn("Failed to send bot failure idle message for '{}'",
                    agent != null ? agent.getName() : "?", chatError);
        }
    }
}
