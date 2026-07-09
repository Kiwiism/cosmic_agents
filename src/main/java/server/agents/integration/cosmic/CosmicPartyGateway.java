package server.agents.integration.cosmic;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.PartyOperation;
import server.agents.integration.AgentPartyMemberSnapshot;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.PartyGateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CosmicPartyGateway implements PartyGateway {
    public static final CosmicPartyGateway INSTANCE = new CosmicPartyGateway();

    private CosmicPartyGateway() {
    }

    @Override
    public void leaveCurrentParty(Character agent) {
        Party.leaveParty(agent.getParty(), agent.getClient());
    }

    @Override
    public boolean createAgentParty(Character leader) {
        return Party.createParty(leader, true);
    }

    @Override
    public boolean joinAgentParty(Character agent, int partyId) {
        return Party.joinParty(agent, partyId, true);
    }

    @Override
    public void publishAgentOnline(Character agent, int partyId) {
        PartyCharacter partyCharacter = new PartyCharacter(agent);
        partyCharacter.setChannel(agent.getClient().getChannel());
        partyCharacter.setMapId(agent.getMapId());
        agent.getWorldServer().updateParty(partyId, PartyOperation.LOG_ONOFF, partyCharacter);
    }

    @Override
    public boolean sendPartyChat(Character speaker, String message) {
        Party party = speaker.getParty();
        if (party == null || speaker.getClient() == null || speaker.getClient().getWorldServer() == null) {
            return false;
        }
        speaker.getClient().getWorldServer().partyChat(party, message, speaker.getName());
        return true;
    }

    @Override
    public AgentPartySnapshot snapshot(Character character) {
        Party party = character.getParty();
        if (party == null) {
            return null;
        }
        List<AgentPartyMemberSnapshot> members = new ArrayList<>();
        for (PartyCharacter member : party.getMembers()) {
            members.add(member == null ? null : new AgentPartyMemberSnapshot(
                    member.getId(), member.getName(), member.isLeader(), member.getMapId()));
        }
        return new AgentPartySnapshot(party.getId(), Collections.unmodifiableList(members));
    }

    @Override
    public boolean hasParty(Character character) {
        return character.getParty() != null;
    }

    @Override
    public List<Character> onlineMembers(Character character) {
        return character.getPartyMembersOnline();
    }
}
