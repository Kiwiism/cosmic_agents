package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotReplyRuntime;
import server.bots.BotEntry;

public final class AgentFormationCommandRuntime {
    private AgentFormationCommandRuntime() {
    }

    public static boolean handleFormationCommand(Character leader,
                                                 String message,
                                                 AgentFormationCommandService.EntriesByLeader entriesByLeader,
                                                 AgentFormationService.FormationState defaultFormation,
                                                 int defaultFollowStaggerPx,
                                                 int defaultSnapRangePx) {
        return AgentFormationCommandService.handleFormationCommand(
                leader,
                message,
                new AgentFormationCommandService.Hooks(
                        entriesByLeader,
                        (leaderCharId, fallbackFormation) -> AgentFormationService.stateForLeader(
                                AgentFormationService.formationsByLeaderId(),
                                leaderCharId,
                                fallbackFormation),
                        AgentFormationService.formationsByLeaderId()::put,
                        AgentFormationService::applyOffsets,
                        (entry, reply) -> AgentBotReplyRuntime.queueReply(asBotEntry(entry), reply),
                        Character::yellowMessage,
                        defaultFormation,
                        defaultFollowStaggerPx,
                        defaultSnapRangePx));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
