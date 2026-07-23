package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.navigation.AgentCollisionPortalService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.social.airshow.AgentAirshowStateRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentTickCadenceStateRuntime;

public final class AgentTickPreflightRuntime {
    private static final long HEARTBEAT_INTERVAL_MS = config.AgentTuning.longValue("server.agents.runtime.AgentTickPreflightRuntime.HEARTBEAT_INTERVAL_MS");

    private AgentTickPreflightRuntime() {
    }

    public static AgentTickPreflightService.Result runPreflight(AgentRuntimeEntry entry,
                                                               int agentCharId,
                                                               long nowMs) {
        if (entry != null && !AgentLifecycleStateRuntime.active(entry)) {
            return new AgentTickPreflightService.Result(true, AgentRuntimeIdentityRuntime.bot(entry), false);
        }
        return AgentTickPreflightService.runPreflight(
                entry,
                agentCharId,
                nowMs,
                hooks());
    }

    private static AgentTickPreflightService.Hooks hooks() {
        return new AgentTickPreflightService.Hooks(
                AgentAirshowStateRuntime::active,
                AgentTickCadenceStateRuntime::consumeSkipDelay,
                AgentRuntimeCleanupService::removeAgentByCharacterId,
                AgentCollisionPortalService::tick,
                (entry, agent, nowMs, heartbeatIntervalMs) -> AgentHeartbeatService.tickHeartbeat(
                        entry,
                        agent,
                        nowMs,
                        heartbeatIntervalMs,
                        heartbeatAgent -> AgentCharacterGatewayRuntime.characters().markClientHeartbeat(heartbeatAgent),
                        AgentMovementBroadcastService::broadcastMovement),
                AgentOfferService::expirePendingOffer,
                (entry, movementTickMs, aiTickMs, tickAtMs) ->
                        AgentTickOrchestrator.prepareTick(entry, movementTickMs, aiTickMs, tickAtMs),
                AgentMovementPhysicsConfig.configuredMovementTickMs(),
                AgentRuntimeConfig.cfg.AI_TICK_MS,
                HEARTBEAT_INTERVAL_MS);
    }
}
