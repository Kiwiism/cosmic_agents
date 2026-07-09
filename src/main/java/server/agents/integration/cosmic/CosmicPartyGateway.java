package server.agents.integration.cosmic;

import client.Character;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.PartyOperation;
import server.agents.integration.PartyGateway;

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
}
