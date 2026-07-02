package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import server.agents.auth.AgentOwnershipService;
import server.bots.BotEntry;

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
                                                                            Consumer<BotEntry> startFollowLeader,
                                                                            Logger log) {
        AgentLifecycleService.RegisterSpawnedAgent registerSpawnedAgent =
                (leaderCharId, spawnLeader, agent) -> AgentRegistrationRuntime.registerAgent(
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
                                                                            Consumer<BotEntry> startFollowLeader,
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
