package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

/**
 * Runtime coordinator for Agent registration, scheduling, formation defaults,
 * and optional spawn-state normalization.
 */
public final class AgentRegistrationCoordinator {
    private AgentRegistrationCoordinator() {
    }

    public static AgentRuntimeEntry registerManualAgent(int leaderCharId,
                                                        Character leader,
                                                        Character agent,
                                                        AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(leaderCharId, leader, agent, false, tickCallback);
    }

    public static AgentRuntimeEntry registerSpawnedAgent(int leaderCharId,
                                                         Character leader,
                                                         Character agent,
                                                         AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(leaderCharId, leader, agent, true, tickCallback);
    }

    public static AgentRuntimeEntry registerStationarySpawnedAgent(int leaderCharId,
                                                                   Character leader,
                                                                   Character agent,
                                                                   AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(leaderCharId, leader, agent, true, tickCallback,
                AgentSpawnPlacementCoordinator::normalizeSpawnedAgentWithoutParty);
    }

    public static AgentRuntimeEntry registerAgent(int leaderCharId,
                                                  Character leader,
                                                  Character agent,
                                                  boolean normalizeSpawnState,
                                                  AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(leaderCharId, leader, agent, normalizeSpawnState, tickCallback,
                AgentSpawnPlacementCoordinator::normalizeSpawnedAgent);
    }

    private static AgentRuntimeEntry registerAgent(int leaderCharId,
                                                   Character leader,
                                                   Character agent,
                                                   boolean normalizeSpawnState,
                                                   AgentLifecycleService.AgentTickCallback tickCallback,
                                                   java.util.function.Consumer<AgentRuntimeEntry> spawnNormalizer) {
        return AgentLifecycleService.registerAgent(
                leaderCharId,
                leader,
                agent,
                normalizeSpawnState,
                new AgentLifecycleService.RegisterHooks(
                        AgentMovementPhysicsConfig.configuredMovementTickMs(),
                        AgentSchedulerRuntime::register,
                        tickCallback,
                        AgentScheduledTaskRuntime::cancelScheduledTask,
                        defaultFormationState(),
                        spawnNormalizer,
                        () -> AgentRandom.randMs(30_000, 31_000)));
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
