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
    public Character findWorldCharacterByName(int world, String characterName) {
        for (var channel : Server.getInstance().getWorld(world).getChannels()) {
            Character candidate = channel.getPlayerStorage().getCharacterByName(characterName);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public Map<Disease, Pair<Long, MobSkill>> loadStoredDiseases(int characterId) {
        return Server.getInstance().getPlayerBuffStorage().getDiseasesFromStorage(characterId);
    }
}
