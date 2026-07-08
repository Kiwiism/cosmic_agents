package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentReplyRuntime;

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
                        AgentReplyRuntime::queueReply,
                        Character::yellowMessage,
                        defaultFormation,
                        defaultFollowStaggerPx,
                        defaultSnapRangePx));
    }
}
