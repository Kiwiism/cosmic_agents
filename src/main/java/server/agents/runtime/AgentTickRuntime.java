package server.agents.runtime;

import server.bots.BotEntry;

import java.util.function.Consumer;

public final class AgentTickRuntime {
    private AgentTickRuntime() {
    }

    public static void tick(BotEntry entry,
                            int leaderCharId,
                            int agentCharId,
                            Consumer<BotEntry> issueGrind,
                            Consumer<BotEntry> issueFollow) {
        AgentTickOrchestrator.runGuardedTick(
                entry,
                leaderCharId,
                agentCharId,
                (tickEntry, tickLeaderId, tickAgentId) -> AgentTickCoreRuntime.tickCore(
                        asBotEntry(tickEntry),
                        tickLeaderId,
                        tickAgentId,
                        grindEntry -> issueGrind.accept(asBotEntry(grindEntry)),
                        followEntry -> issueFollow.accept(asBotEntry(followEntry))),
                (failedEntry, failedLeaderId, failedAgentId, failure) -> AgentTickFailureRuntime.handleFailure(
                        asBotEntry(failedEntry),
                        failedLeaderId,
                        failedAgentId,
                        failure));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
