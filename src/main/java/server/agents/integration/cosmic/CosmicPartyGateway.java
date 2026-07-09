package server.agents.integration.cosmic;

import client.Character;
import net.server.world.Party;
import server.agents.integration.PartyGateway;

public final class CosmicPartyGateway implements PartyGateway {
    public static final CosmicPartyGateway INSTANCE = new CosmicPartyGateway();

    private CosmicPartyGateway() {
    }

    @Override
    public void leaveCurrentParty(Character agent) {
        Party.leaveParty(agent.getParty(), agent.getClient());
    }
}
