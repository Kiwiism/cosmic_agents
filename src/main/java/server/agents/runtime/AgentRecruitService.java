package server.agents.runtime;

import client.Character;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.registry.AgentResolvedCharacter;

public final class AgentRecruitService {
    private AgentRecruitService() {
    }

    public record Hooks(OwnerlessAgentLookup ownerlessAgentLookup,
                        RecruitAuthorization recruitAuthorization,
                        AgentRegistrar agentRegistrar) {
    }

    @FunctionalInterface
    public interface OwnerlessAgentLookup {
        Character find(String agentName, int world);
    }

    @FunctionalInterface
    public interface RecruitAuthorization {
        AgentAuthorizationResult authorize(Character leader, AgentResolvedCharacter agent);
    }

    @FunctionalInterface
    public interface AgentRegistrar {
        void register(int leaderCharId, Character leader, Character agent);
    }

    public static String recruitAgent(int leaderCharId, Character leader, String agentName, Hooks hooks) {
        Character agent = hooks.ownerlessAgentLookup().find(agentName, leader.getWorld());
        if (agent == null) {
            return "No ownerless bot named '" + agentName + "' found.";
        }

        AgentAuthorizationResult auth = hooks.recruitAuthorization().authorize(
                leader,
                new AgentResolvedCharacter(
                        agent.getId(),
                        agent.getName(),
                        agent.getAccountID(),
                        agent));
        if (!auth.allowed()) {
            return auth.failureMessage();
        }

        hooks.agentRegistrar().register(leaderCharId, leader, agent);
        return null;
    }
}
