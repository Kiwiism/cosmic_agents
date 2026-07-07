package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

import java.util.List;

public final class AgentFollowAnchorService {
    private AgentFollowAnchorService() {
    }

    public static Character resolve(AgentRuntimeEntry entry,
                                    Character leader,
                                    List<? extends AgentRuntimeEntry> siblingEntries) {
        if (leader == null) {
            return null;
        }

        return resolveTarget(entry, leader, AgentBotModeStateRuntime.followTargetId(entry), siblingEntries);
    }

    public static Character resolveTarget(AgentRuntimeEntry entry,
                                          Character leader,
                                          int targetId,
                                          List<? extends AgentRuntimeEntry> siblingEntries) {
        if (leader == null) {
            return null;
        }
        if (targetId <= 0 || targetId == leader.getId() || targetId == AgentBotRuntimeIdentityRuntime.botId(entry)) {
            return leader;
        }

        if (leader.getParty() != null) {
            for (Character member : leader.getPartyMembersOnline()) {
                if (member != null && member.getId() == targetId && member.isLoggedinWorld()) {
                    return member;
                }
            }
        }

        if (siblingEntries != null) {
            for (AgentRuntimeEntry sibling : siblingEntries) {
                Character siblingAgent = AgentBotRuntimeIdentityRuntime.bot(sibling);
                if (siblingAgent != null && siblingAgent.getId() == targetId && siblingAgent.isLoggedinWorld()) {
                    return siblingAgent;
                }
            }
        }

        return leader;
    }

    public static Character resolveTargetFromRuntimeRegistry(AgentRuntimeEntry entry, int targetId) {
        Character leader = AgentBotRuntimeIdentityRuntime.owner(entry);
        List<? extends AgentRuntimeEntry> siblingEntries = leader == null
                ? List.of()
                : AgentRuntimeRegistry.agentEntriesForLeader(leader.getId());
        return resolveTarget(entry, leader, targetId, siblingEntries);
    }
}
