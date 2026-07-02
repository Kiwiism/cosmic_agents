package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;

public final class AgentFollowTargetCandidateService {
    private AgentFollowTargetCandidateService() {
    }

    public record Hooks(SiblingEntries siblingEntries) {
    }

    @FunctionalInterface
    public interface SiblingEntries {
        List<BotEntry> entries(int leaderCharId);
    }

    public static List<Character> candidates(Character leader, Hooks hooks) {
        List<Character> candidates = new ArrayList<>();
        if (leader.isLoggedinWorld()) {
            candidates.add(leader);
        }
        if (leader.getParty() != null) {
            for (Character member : leader.getPartyMembersOnline()) {
                if (member == null || !member.isLoggedinWorld() || member.getId() == leader.getId()) {
                    continue;
                }
                candidates.add(member);
            }
        }
        for (BotEntry sibling : hooks.siblingEntries().entries(leader.getId())) {
            Character siblingAgent = AgentBotRuntimeIdentityRuntime.bot(sibling);
            if (siblingAgent == null || !siblingAgent.isLoggedinWorld()) {
                continue;
            }
            boolean duplicate = false;
            for (Character candidate : candidates) {
                if (candidate.getId() == siblingAgent.getId()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                candidates.add(siblingAgent);
            }
        }
        return candidates;
    }
}
