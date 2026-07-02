package server.agents.runtime;

import client.Character;
import server.TimerManager;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

/**
 * Temporary legacy hook bundle for Agent registration scheduling while the live
 * tick callback is still supplied by the BotManager compatibility shell.
 */
public final class AgentRegistrationRuntime {
    private AgentRegistrationRuntime() {
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
                        BotMovementManager.configuredTickMs(),
                        TimerManager.getInstance()::register,
                        tickCallback,
                        AgentBotManagerSchedulerRuntime::cancelScheduledTask,
                        defaultFormationState(),
                        AgentSpawnPlacementRuntime::normalizeSpawnedAgent,
                        () -> AgentRandom.randMs(30_000, 31_000)));
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                BotMovementManager.configuredFollowYCap());
    }
}
