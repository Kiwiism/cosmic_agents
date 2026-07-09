package server.agents.integration;

import client.Character;
import client.Disease;
import server.life.MobSkill;
import tools.Pair;

import java.util.Map;

public interface CharacterGateway {
    Character findWorldCharacterById(int world, int characterId);

    Character findWorldCharacterByName(int world, String characterName);

    Map<Disease, Pair<Long, MobSkill>> loadStoredDiseases(int characterId);
}

