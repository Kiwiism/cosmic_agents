package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import server.agents.auth.AgentOwnershipService;
import server.agents.integration.AgentMapGatewayRuntime;

import java.util.function.Consumer;

/**
 * Temporary Cosmic hook bundle for spawning an Agent while registration still
 * receives tick and follow-start callbacks from the Agent runtime facade.
 */
public final class AgentSpawnRuntime {
    private AgentSpawnRuntime() {
    }

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(Character leader,
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

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(Character leader,
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
                        AgentOfflineLoadRuntime::loadOfflineAgent,
                        AgentSpawnPlacementCoordinator::placeSpawnedOnlineAgent,
                        startFollowLeader,
                        AgentMapGatewayRuntime.map()::changeMapNear),
                (failedAgentName, failedLeader, e) -> log.warn(
                        "Failed to load bot character '{}' for owner '{}'", failedAgentName, failedLeader.getName(), e));
    }
}
