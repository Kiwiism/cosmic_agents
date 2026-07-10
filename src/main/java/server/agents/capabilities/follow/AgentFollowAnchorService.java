package server.agents.capabilities.follow;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

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

        return resolveTarget(entry, leader, AgentModeStateRuntime.followTargetId(entry), siblingEntries);
    }

    public static Character resolveTarget(AgentRuntimeEntry entry,
                                          Character leader,
                                          int targetId,
                                          List<? extends AgentRuntimeEntry> siblingEntries) {
        if (leader == null) {
            return null;
        }
        if (targetId <= 0 || targetId == leader.getId() || targetId == AgentRuntimeIdentityRuntime.botId(entry)) {
            return leader;
        }

        if (AgentPartyGatewayRuntime.party().hasParty(leader)) {
            for (Character member : AgentPartyGatewayRuntime.party().onlineMembers(leader)) {
                if (member != null && member.getId() == targetId && member.isLoggedinWorld()) {
                    return member;
                }
            }
        }

        if (siblingEntries != null) {
            for (AgentRuntimeEntry sibling : siblingEntries) {
                Character siblingAgent = AgentRuntimeIdentityRuntime.bot(sibling);
                if (siblingAgent != null && siblingAgent.getId() == targetId && siblingAgent.isLoggedinWorld()) {
                    return siblingAgent;
                }
            }
        }

        return leader;
    }

    public static Character resolveTargetFromRuntimeRegistry(AgentRuntimeEntry entry, int targetId) {
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        List<? extends AgentRuntimeEntry> siblingEntries = leader == null
                ? List.of()
                : AgentRuntimeRegistry.agentEntriesForLeader(leader.getId());
        return resolveTarget(entry, leader, targetId, siblingEntries);
    }
}
