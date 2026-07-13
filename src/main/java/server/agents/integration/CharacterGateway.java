package server.agents.integration;

import client.Character;
import client.Disease;
import server.life.MobSkill;
import tools.Pair;

import java.util.Map;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Online lookup, heartbeat, disconnect, and Agent identity use existing concurrent Cosmic APIs.")
public interface CharacterGateway {
    Character findWorldCharacterById(int world, int characterId);

    Character findWorldCharacterByName(int world, String characterName);

    Character findOnlineCharacterById(int characterId);

    Character findOnlineCharacterByName(String characterName);

    @AgentGatewayAffinity(
            value = AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
            rationale = "Stored disease loading performs persistence work during lifecycle loading.")
    Map<Disease, Pair<Long, MobSkill>> loadStoredDiseases(int characterId);

    void markClientHeartbeat(Character agent);

    @AgentGatewayAffinity(
            value = AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
            rationale = "Character saving performs database I/O and is not scheduler work.")
    void save(Character agent, boolean positionOnly);

    void disconnect(Character agent, boolean shutdown, boolean cashShop);

    boolean isAgentCharacter(Character character);
}

