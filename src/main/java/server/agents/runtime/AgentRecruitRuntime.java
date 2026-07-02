package server.agents.runtime;

import client.Character;
import server.agents.auth.AgentOwnershipService;

/**
 * Temporary legacy hook bundle for ownerless Agent recruitment while
 * registration still uses compatibility-shaped Agent lifecycle callbacks.
 */
public final class AgentRecruitRuntime {
    private AgentRecruitRuntime() {
    }

    public static String recruitAgent(int leaderCharId,
                                      Character leader,
                                      String agentName,
                                      AgentRecruitService.AgentRegistrar registrar) {
        return AgentRecruitService.recruitAgent(
                leaderCharId,
                leader,
                agentName,
                new AgentRecruitService.Hooks(
                        AgentRuntimeRegistry::findUnclaimedOnlineAgentByName,
                        (candidateLeader, agent) -> AgentOwnershipService.getInstance()
                                .ensureCanControl(candidateLeader, agent),
                        registrar));
    }
}
