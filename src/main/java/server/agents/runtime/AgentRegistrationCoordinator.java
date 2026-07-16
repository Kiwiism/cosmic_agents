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

    public static AgentRuntimeEntry registerAgent(int leaderCharId,
                                                  Character leader,
                                                  Character agent,
                                                  boolean normalizeSpawnState,
                                                  AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(
                leaderCharId, leader, agent, normalizeSpawnState, tickCallback, false);
    }

    public static AgentRuntimeEntry registerPartnerAgent(int leaderCharId,
                                                         Character leader,
                                                         Character agent,
                                                         boolean normalizeSpawnState,
                                                         AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(
                leaderCharId, leader, agent, normalizeSpawnState, tickCallback, true);
    }

    private static AgentRuntimeEntry registerAgent(int leaderCharId,
                                                   Character leader,
                                                   Character agent,
                                                   boolean normalizeSpawnState,
                                                   AgentLifecycleService.AgentTickCallback tickCallback,
                                                   boolean partnerManaged) {
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
                        AgentSpawnPlacementCoordinator::normalizeSpawnedAgent,
                        () -> AgentRandom.randMs(30_000, 31_000)),
                entry -> {
                    if (partnerManaged) {
                        entry.markPartnerManaged();
                    }
                });
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
