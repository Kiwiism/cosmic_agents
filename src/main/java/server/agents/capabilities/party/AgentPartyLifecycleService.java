package server.agents.capabilities.party;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;

/**
 * Agent-owned party lifecycle side effects for live Agent sessions.
 */
public final class AgentPartyLifecycleService {
    private AgentPartyLifecycleService() {
    }

    public static void joinAgentToLeaderParty(Character leader, Character agent) {
        AgentPartySnapshot agentParty = AgentPartyGatewayRuntime.party().snapshot(agent);
        if (agentParty != null) {
            AgentPartySnapshot leaderParty = AgentPartyGatewayRuntime.party().snapshot(leader);
            if (leaderParty != null && agentParty.id() == leaderParty.id()) {
                AgentPartyGatewayRuntime.party().publishAgentOnline(agent, leaderParty.id());
                agent.updatePartyMemberHP();
                return;
            }
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
        AgentPartySnapshot leaderParty = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (leaderParty == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(leader)) {
                return;
            }
            leaderParty = AgentPartyGatewayRuntime.party().snapshot(leader);
        }
        if (leaderParty == null) {
            return;
        }
        if (AgentPartyGatewayRuntime.party().joinAgentParty(agent, leaderParty.id())) {
            agent.updatePartyMemberHP();
        }
    }

    public static void leaveAgentParty(Character agent) {
        if (agent != null && AgentPartyGatewayRuntime.party().snapshot(agent) != null) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
    }
}
