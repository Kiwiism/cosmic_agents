package server.agents.integration.cosmic;

import client.Character;
import client.BotClient;
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
    public Character findOnlineCharacterById(int characterId) {
        for (var world : Server.getInstance().getWorlds()) {
            Character online = world.getPlayerStorage().getCharacterById(characterId);
            if (online != null) {
                return online;
            }
        }
        return null;
    }

    @Override
    public Character findOnlineCharacterByName(String characterName) {
        for (var world : Server.getInstance().getWorlds()) {
            Character online = world.getPlayerStorage().getCharacterByName(characterName);
            if (online != null) {
                return online;
            }
        }
        return null;
    }

    @Override
    public Map<Disease, Pair<Long, MobSkill>> loadStoredDiseases(int characterId) {
        return Server.getInstance().getPlayerBuffStorage().getDiseasesFromStorage(characterId);
    }

    @Override
    public void markClientHeartbeat(Character agent) {
        if (agent != null && agent.getClient() != null) {
            agent.getClient().updateLastPacket();
        }
    }

    @Override
    public void save(Character agent, boolean positionOnly) {
        if (agent != null) {
            agent.saveCharToDB(positionOnly);
        }
    }

    @Override
    public void disconnect(Character agent, boolean shutdown, boolean cashShop) {
        if (agent != null && agent.getClient() != null) {
            agent.getClient().disconnect(shutdown, cashShop);
        }
    }

    @Override
    public boolean isAgentCharacter(Character character) {
        return character != null && character.getClient() instanceof BotClient;
    }
}
