package server.agents.runtime;

import client.Character;
import server.TimerManager;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.bots.BotEntry;

/**
 * Temporary legacy hook bundle for Agent registration scheduling while the live
 * tick callback is supplied by the Agent runtime facade.
 */
public final class AgentRegistrationRuntime {
    private AgentRegistrationRuntime() {
    }

    public static BotEntry registerManualAgent(int leaderCharId,
                                               Character leader,
                                               Character agent,
                                               AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(leaderCharId, leader, agent, false, tickCallback);
    }

    public static BotEntry registerSpawnedAgent(int leaderCharId,
                                                Character leader,
                                                Character agent,
                                                AgentLifecycleService.AgentTickCallback tickCallback) {
        return registerAgent(leaderCharId, leader, agent, true, tickCallback);
    }

    public static BotEntry registerAgent(int leaderCharId,
                                         Character leader,
                                         Character agent,
                                         boolean normalizeSpawnState,
                                         AgentLifecycleService.AgentTickCallback tickCallback) {
        return AgentLifecycleService.registerAgent(
                leaderCharId,
                leader,
                agent,
                normalizeSpawnState,
                new AgentLifecycleService.RegisterHooks(
                        AgentMovementPhysicsConfig.configuredMovementTickMs(),
                        TimerManager.getInstance()::register,
                        tickCallback,
                        AgentScheduledTaskRuntime::cancelScheduledTask,
                        defaultFormationState(),
                        AgentSpawnPlacementRuntime::normalizeSpawnedAgent,
                        () -> AgentRandom.randMs(30_000, 31_000)));
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
