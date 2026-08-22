package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.progression.AgentCareerBuildBundleService;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.AgentCareerProgressionCheckpointRuntime;
import server.agents.objectives.AgentObjectiveCheckpointRuntime;
import server.agents.capabilities.townlife.AgentTownLifeCheckpointRuntime;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseCheckpointRuntime;
import server.agents.runtime.field.AgentFieldCheckpointRuntime;
import server.agents.runtime.field.AgentFieldVisitLeaseCheckpointRuntime;
import server.agents.plans.AgentPlanReattachmentRuntime;
import server.agents.plans.AgentPlanCheckpointRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.activity.AgentActivityOwnershipReconciliation;
import server.agents.runtime.activity.control.AgentWorldDirectorModeRestoreRuntime;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime coordinator for Agent registration, scheduling, formation defaults,
 * and optional spawn-state normalization.
 */
public final class AgentRegistrationCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AgentRegistrationCoordinator.class);
    private static final long WORLD_DIRECTOR_OBSERVE_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.runtime.AgentRegistrationCoordinator.WORLD_DIRECTOR_OBSERVE_INTERVAL_MS");
    private static final AgentWorldDirectorModeRestoreRuntime WORLD_DIRECTOR_MODES =
            new AgentWorldDirectorModeRestoreRuntime(
                    AgentFileWorldDirectorSessionStore.runtimeDefault(),
                    WORLD_DIRECTOR_OBSERVE_INTERVAL_MS);
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
        AgentPlanCheckpointRuntime.restore(entry);
        AgentTownLifeCheckpointRuntime.restore(entry, agent, nowMs);
        AgentTownLifeVisitLeaseCheckpointRuntime.restore(entry, agent);
        AgentFieldCheckpointRuntime.restore(entry, agent, nowMs);
        AgentFieldVisitLeaseCheckpointRuntime.restore(entry, agent);
        WORLD_DIRECTOR_MODES.restore(entry, agent.getId(), nowMs);
        AgentActivityOwnershipReconciliation ownership = entry.capabilityStates() == null
                ? null : AgentActivityBootstrap.reconcileRestoredOwnership(entry, agent, nowMs);
        if (ownership == null || ownership.permitsExecution()) {
            AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, nowMs);
        } else {
            log.warn("Agent '{}' registration paused by retained ownership reconciliation: {} {}",
                    agent.getName(), ownership.status(), ownership.reason());
        }
        return entry;
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
