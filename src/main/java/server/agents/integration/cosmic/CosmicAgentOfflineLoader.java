package server.agents.integration.cosmic;

import client.Character;
import config.YamlConfig;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.runtime.AgentSpawnPositionService;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;

/**
 * Cosmic adapter for loading an offline backing character into an Agent runtime
 * session and registering it with channel, world, and map storage.
 */
public final class CosmicAgentOfflineLoader {
    private CosmicAgentOfflineLoader() {
    }

    public static Character loadOfflineAgent(int characterId,
                                             int world,
                                             int channel,
                                             MapleMap targetMap,
                                             Point desiredPosition) throws SQLException {
        return loadOfflineAgent(
                characterId,
                world,
                channel,
                targetMap,
                desiredPosition,
                hooks());
    }

    static Character loadOfflineAgent(int characterId,
                                      int world,
                                      int channel,
                                      MapleMap targetMap,
                                      Point desiredPosition,
                                      CosmicAgentOfflineLoadService.Hooks hooks) throws SQLException {
        return CosmicAgentOfflineLoadService.loadOfflineAgent(
                characterId,
                world,
                channel,
                targetMap,
                desiredPosition,
                hooks);
    }

    private static CosmicAgentOfflineLoadService.Hooks hooks() {
        return new CosmicAgentOfflineLoadService.Hooks(
                AgentClientGatewayRuntime.clients()::createHeadlessClient,
                AgentClientGatewayRuntime.clients()::loadBackingCharacter,
                AgentCharacterGatewayRuntime.characters()::loadStoredDiseases,
                AgentMapGatewayRuntime.map()::resolveMap,
                AgentSpawnPositionService::resolveSpawnPosition,
                agent -> {
                    agent.resetPlayerRates();
                    if (YamlConfig.config.server.USE_ADD_RATES_BY_LEVEL) {
                        agent.setPlayerRates();
                    }
                    agent.setWorldRates();
                    agent.updateCouponRates();
                },
                AgentMapGatewayRuntime.map()::addChannelPlayer,
                AgentMapGatewayRuntime.map()::addWorldPlayer,
                AgentMapGatewayRuntime.map()::addMapPlayer);
    }
}
