package server.agents.runtime;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.PartyOperation;
import server.agents.integration.AgentPartyGatewayRuntime;

/**
 * Agent-owned party lifecycle side effects for live Agent sessions.
 */
public final class AgentPartyLifecycleService {
    private AgentPartyLifecycleService() {
    }

    public static void joinAgentToLeaderParty(Character leader, Character agent) {
        Party agentParty = agent.getParty();
        if (agentParty != null) {
            Party leaderParty = leader.getParty();
            if (leaderParty != null && agentParty.getId() == leaderParty.getId()) {
                PartyCharacter partyCharacter = new PartyCharacter(agent);
                partyCharacter.setChannel(agent.getClient().getChannel());
                partyCharacter.setMapId(agent.getMapId());
                agent.getWorldServer().updateParty(leaderParty.getId(), PartyOperation.LOG_ONOFF, partyCharacter);
                agent.updatePartyMemberHP();
                return;
            }
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
        Party leaderParty = leader.getParty();
        if (leaderParty == null) {
            if (!Party.createParty(leader, true)) {
                return;
            }
            leaderParty = leader.getParty();
        }
        if (leaderParty == null) {
            return;
        }
        if (Party.joinParty(agent, leaderParty.getId(), true)) {
            agent.updatePartyMemberHP();
        }
    }
}
