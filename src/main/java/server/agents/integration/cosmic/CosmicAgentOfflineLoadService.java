package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import client.Disease;
import server.life.MobSkill;
import server.maps.MapleMap;
import tools.Pair;

import java.awt.Point;
import java.sql.SQLException;
import java.util.Map;

public final class CosmicAgentOfflineLoadService {
    private CosmicAgentOfflineLoadService() {
    }

    public record Hooks(AgentClientFactory clientFactory,
                        CharacterLoader characterLoader,
                        DiseaseStorageLoader diseaseStorageLoader,
                        MapResolver mapResolver,
                        SpawnPositionResolver spawnPositionResolver,
                        RateInitializer rateInitializer,
                        ChannelPlayerRegistrar channelPlayerRegistrar,
                        WorldPlayerRegistrar worldPlayerRegistrar,
                        MapPlayerRegistrar mapPlayerRegistrar) {
    }

    @FunctionalInterface
    public interface AgentClientFactory {
        Client create(int world, int channel);
    }

    @FunctionalInterface
    public interface CharacterLoader {
        Character load(int characterId, Client client) throws SQLException;
    }

    @FunctionalInterface
    public interface DiseaseStorageLoader {
        Map<Disease, Pair<Long, MobSkill>> load(int characterId);
    }

    @FunctionalInterface
    public interface MapResolver {
        MapleMap resolve(int world, int channel, int mapId);
    }

    @FunctionalInterface
    public interface SpawnPositionResolver {
        Point resolve(MapleMap map, Point desiredPosition);
    }

    @FunctionalInterface
    public interface RateInitializer {
        void initialize(Character agent);
    }

    @FunctionalInterface
    public interface ChannelPlayerRegistrar {
        void add(int world, int channel, Character agent);
    }

    @FunctionalInterface
    public interface WorldPlayerRegistrar {
        void add(int world, int channel, Character agent);
    }

    @FunctionalInterface
    public interface MapPlayerRegistrar {
        void add(MapleMap map, Character agent);
    }

    public static Character loadOfflineAgent(int characterId,
                                             int world,
                                             int channel,
                                             MapleMap targetMap,
                                             Point desiredPosition,
                                             Hooks hooks) throws SQLException {
        Client client = hooks.clientFactory().create(world, channel);
        Character agent = hooks.characterLoader().load(characterId, client);
        client.setPlayer(agent);
        client.setAccID(agent.getAccountID());

        Map<Disease, Pair<Long, MobSkill>> diseases = hooks.diseaseStorageLoader().load(characterId);
        if (diseases != null) {
            agent.silentApplyDiseases(diseases);
        }

        MapleMap spawnMap = targetMap != null ? targetMap : hooks.mapResolver().resolve(world, channel, agent.getMapId());
        Point desired = desiredPosition != null ? desiredPosition : agent.getPosition();
        Point spawnPosition = hooks.spawnPositionResolver().resolve(spawnMap, desired);

        agent.setMapId(spawnMap.getId());
        agent.newClient(client);
        agent.recalcLocalStats();
        hooks.rateInitializer().initialize(agent);
        agent.setPosition(spawnPosition);

        hooks.channelPlayerRegistrar().add(world, channel, agent);
        hooks.worldPlayerRegistrar().add(world, channel, agent);
        agent.setEnteredChannelWorld();
        hooks.mapPlayerRegistrar().add(spawnMap, agent);
        agent.visitMap(spawnMap);
        agent.diseaseExpireTask();
        return agent;
    }
}
