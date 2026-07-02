package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import server.agents.auth.AgentOwnershipService;
import server.bots.BotEntry;

/**
 * Temporary Cosmic hook bundle for spawning an Agent while BotManager still
 * supplies spawned registration and follow-start callbacks.
 */
public final class AgentSpawnRuntime {
    private AgentSpawnRuntime() {
    }

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(Character leader,
                                                                            String agentName,
                                                                            AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent,
                                                                            java.util.function.Consumer<BotEntry> startFollowLeader,
                                                                            Logger log) {
        return AgentLifecycleService.spawnAgentForLeaderQuietly(
                leader,
                agentName,
                AgentOwnershipService.getInstance(),
                new AgentLifecycleService.SpawnHooks(
                        AgentSpawnPositionService::resolveSpawnPosition,
                        registerSpawnedAgent,
                        AgentOfflineLoadRuntime::loadOfflineAgent,
                        AgentSpawnPlacementRuntime::placeSpawnedOnlineAgent,
                        startFollowLeader,
                        (agent, map, position) -> agent.forceChangeMap(map, map.findClosestPortal(position))),
                (failedAgentName, failedLeader, e) -> log.warn(
                        "Failed to load bot character '{}' for owner '{}'", failedAgentName, failedLeader.getName(), e));
    }
}
