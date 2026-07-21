package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.progression.AgentCareerBuildBundleService;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.AgentCareerProgressionCheckpointRuntime;
import server.agents.objectives.AgentObjectiveCheckpointRuntime;
import server.agents.plans.AgentPlanReattachmentRuntime;

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
        AgentRuntimeEntry entry = AgentLifecycleService.registerAgent(
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
        long nowMs = System.currentTimeMillis();
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleService.restoreOrAssign(entry, nowMs);
        AgentPersonalityRuntime.restoreOrAssign(entry, false, nowMs);
        AgentObjectiveCheckpointRuntime.restore(entry);
        AgentCareerProgressionCheckpointRuntime.restore(entry, bundle);
        AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, nowMs);
        return entry;
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
