package server.agents.runtime;

import client.BotClient;
import client.Character;
import config.YamlConfig;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;

/**
 * Temporary Cosmic hook bundle for loading offline backing characters into the
 * Agent runtime while persistence/bootstrap wiring is reconstructed.
 */
public final class AgentOfflineLoadRuntime {
    private AgentOfflineLoadRuntime() {
    }

    public static Character loadOfflineAgent(int characterId,
                                             int world,
                                             int channel,
                                             MapleMap targetMap,
                                             Point desiredPosition) throws SQLException {
        return AgentOfflineLoadService.loadOfflineAgent(
                characterId,
                world,
                channel,
                targetMap,
                desiredPosition,
                hooks());
    }

    private static AgentOfflineLoadService.Hooks hooks() {
        return new AgentOfflineLoadService.Hooks(
                BotClient::new,
                (characterId, client) -> Character.loadCharFromDB(characterId, client, true),
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
