package server.agents.integration.cosmic;

import client.Character;
import client.Disease;
import net.server.Server;
import server.agents.integration.CharacterGateway;
import server.life.MobSkill;
import tools.Pair;

import java.util.Map;

public enum CosmicCharacterGateway implements CharacterGateway {
    INSTANCE;

    @Override
    public Character findWorldCharacterById(int world, int characterId) {
        return Server.getInstance()
                .getWorld(world)
                .getPlayerStorage()
                .getCharacterById(characterId);
    }

    @Override
    public Map<Disease, Pair<Long, MobSkill>> loadStoredDiseases(int characterId) {
        return Server.getInstance().getPlayerBuffStorage().getDiseasesFromStorage(characterId);
    }
}
