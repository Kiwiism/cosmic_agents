package server.agents.integration.cosmic;

import client.Character;
import org.slf4j.Logger;
import server.agents.auth.AgentOwnershipService;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRegistrationCoordinator;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSpawnPlacementCoordinator;
import server.agents.runtime.AgentSpawnPositionService;

import java.util.function.Consumer;

/**
 * Cosmic adapter coordinating Agent spawn lifecycle hooks with map placement and
 * offline backing-character loading.
 */
public final class CosmicAgentSpawnCoordinator {
    private CosmicAgentSpawnCoordinator() {
    }

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(
            Character leader,
            String agentName,
            AgentLifecycleService.AgentTickCallback tickCallback,
            Consumer<AgentRuntimeEntry> startFollowLeader,
            Logger log) {
        AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent =
                (leaderCharId, spawnLeader, agent) -> AgentRegistrationCoordinator.registerAgent(
                        leaderCharId,
                        spawnLeader,
                        agent,
                        true,
                        tickCallback);
        return spawnAgentForLeader(
                leader,
                agentName,
                registerSpawnedAgent,
                startFollowLeader,
                log);
    }

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(
            Character leader,
            String agentName,
            AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent,
            Consumer<AgentRuntimeEntry> startFollowLeader,
            Logger log) {
        return AgentLifecycleService.spawnAgentForLeaderQuietly(
                leader,
                agentName,
                AgentOwnershipService.getInstance(),
                new AgentLifecycleService.SpawnHooks(
                        AgentSpawnPositionService::resolveSpawnPosition,
                        registerSpawnedAgent,
                        CosmicAgentOfflineLoader::loadOfflineAgent,
                        AgentSpawnPlacementCoordinator::placeSpawnedOnlineAgent,
                        startFollowLeader,
                        AgentMapGatewayRuntime.map()::changeMapNear),
                (failedAgentName, failedLeader, e) -> log.warn(
                        "Failed to load bot character '{}' for owner '{}'", failedAgentName, failedLeader.getName(), e));
    }
}
