package server.agents.capabilities.dialogue.llm;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyMemberSnapshot;
import server.agents.integration.AgentPartySnapshot;

public enum AgentSenderRelation {
    OWNER, PARTY, STRANGER;

    public static AgentSenderRelation resolve(Character agent, Character leader, Character sender) {
        if (agent == null || sender == null) {
            return STRANGER;
        }
        if (leader != null && leader.getId() == sender.getId()) {
            return OWNER;
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(agent);
        if (party != null) {
            for (AgentPartyMemberSnapshot member : party.members()) {
                if (member.id() == sender.getId()) {
                    return PARTY;
                }
            }
        }
        return STRANGER;
    }
}
