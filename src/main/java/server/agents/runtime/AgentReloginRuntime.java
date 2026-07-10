package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;

/**
 * Temporary Cosmic hook bundle for reloading an offline Agent while the Agent
 * interaction runtime supplies the tick callback.
 */
public final class AgentReloginRuntime {
    private AgentReloginRuntime() {
    }

    public static void reloginAgent(int agentCharId,
                                    int leaderCharId,
                                    int world,
                                    int channel,
                                    AgentLifecycleService.AgentTickCallback tickCallback,
                                    Logger log) {
        AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent =
                (registeredLeaderCharId, leader, agent) -> AgentRegistrationCoordinator.registerAgent(
                        registeredLeaderCharId,
                        leader,
                        agent,
                        true,
                        tickCallback);
        reloginAgent(
                agentCharId,
                leaderCharId,
                world,
                channel,
                registerSpawnedAgent,
                log);
    }

    public static void reloginAgent(int agentCharId,
                                    int leaderCharId,
                                    int world,
                                    int channel,
                                    AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent,
                                    Logger log) {
        AgentLifecycleService.reloginAgentQuietly(
                agentCharId,
                leaderCharId,
                world,
                channel,
                new AgentLifecycleService.ReloginHooks(
                        (targetWorld, targetLeaderCharId) -> AgentCharacterGatewayRuntime.characters()
                                .findWorldCharacterById(targetWorld, targetLeaderCharId),
                        AgentSpawnPositionService::resolveSpawnPosition,
                        AgentReloginRuntime::loadOfflineAgent,
                        registerSpawnedAgent,
                        AgentSchedulerRuntime::afterDelay,
                        () -> AgentRandom.randMs(900, 1100),
                        AgentReplyRuntime::sayMapNow),
                (failedAgentCharId, e) -> log.warn("reloginBot: failed to reload charId={}", failedAgentCharId, e));
    }

    private static Character loadOfflineAgent(int agentCharId,
                                              int world,
                                              int channel,
                                              MapleMap targetMap,
                                              Point desiredPosition) throws SQLException {
        return AgentOfflineLoadRuntime.loadOfflineAgent(agentCharId, world, channel, targetMap, desiredPosition);
    }
}
