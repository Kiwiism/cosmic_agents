package server.agents.integration.cosmic;

import client.Character;
import org.slf4j.Logger;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentReloginRequest;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRegistrationCoordinator;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;

/**
 * Cosmic adapter coordinating Agent relogin with world lookup, offline loading,
 * delayed scheduling, and map reply delivery.
 */
public final class CosmicAgentReloginCoordinator {
    private CosmicAgentReloginCoordinator() {
    }

    public static void reloginAgent(AgentReloginRequest request,
                                    AgentLifecycleService.AgentTickCallback tickCallback,
                                    Logger log) {
        AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent =
                (cohortId, interactionTarget, agent) -> AgentRegistrationCoordinator.registerAgent(
                        cohortId, interactionTarget, agent, true, tickCallback);
        AgentLifecycleService.reloginAgentQuietly(
                request,
                new AgentLifecycleService.AgentReloginHooks(
                        server.agents.integration.AgentMapGatewayRuntime.map()::resolveMap,
                        (world, characterId) -> AgentCharacterGatewayRuntime.characters()
                                .findWorldCharacterById(world, characterId),
                        server.agents.runtime.AgentSpawnPositionService::resolveSpawnPosition,
                        CosmicAgentReloginCoordinator::loadOfflineAgent,
                        registerSpawnedAgent,
                        AgentSchedulerRuntime::afterDelay,
                        () -> AgentRandom.randMs(900, 1100),
                        AgentReplyRuntime::sayMapNow),
                (failedAgentCharId, e) -> log.warn(
                        "reloginAgent: failed to reload charId={}", failedAgentCharId, e));
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
                        server.agents.runtime.AgentSpawnPositionService::resolveSpawnPosition,
                        CosmicAgentReloginCoordinator::loadOfflineAgent,
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
        return CosmicAgentOfflineLoader.loadOfflineAgent(agentCharId, world, channel, targetMap, desiredPosition);
    }
}
