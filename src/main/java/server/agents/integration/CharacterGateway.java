package server.agents.integration;

import client.Character;

public interface CharacterGateway {
    Character findWorldCharacterById(int world, int characterId);
}

