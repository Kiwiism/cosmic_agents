package server.agents.integration.cosmic;

import client.Character;
import net.server.Server;
import server.agents.integration.CharacterGateway;

public enum CosmicCharacterGateway implements CharacterGateway {
    INSTANCE;

    @Override
    public Character findWorldCharacterById(int world, int characterId) {
        return Server.getInstance()
                .getWorld(world)
                .getPlayerStorage()
                .getCharacterById(characterId);
    }
}
