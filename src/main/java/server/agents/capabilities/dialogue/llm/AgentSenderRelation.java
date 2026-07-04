package server.agents.capabilities.dialogue.llm;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;

public enum AgentSenderRelation {
    OWNER, PARTY, STRANGER;

    public static AgentSenderRelation resolve(Character agent, Character leader, Character sender) {
        if (agent == null || sender == null) {
            return STRANGER;
        }
        if (leader != null && leader.getId() == sender.getId()) {
            return OWNER;
        }
        Party party = agent.getParty();
        if (party != null) {
            for (PartyCharacter member : party.getMembers()) {
                if (member.getId() == sender.getId()) {
                    return PARTY;
                }
            }
        }
        return STRANGER;
    }
}
