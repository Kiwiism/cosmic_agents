package server.agents.runtime;

import server.agents.capabilities.trade.AgentOfferService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentTickPreflightRuntime {
    private static final long HEARTBEAT_INTERVAL_MS = 600_000L;

    private AgentTickPreflightRuntime() {
    }

    public static AgentTickPreflightService.Result runPreflight(BotEntry entry,
                                                               int agentCharId,
                                                               long nowMs) {
        return AgentTickPreflightService.runPreflight(
                entry,
                agentCharId,
                nowMs,
                hooks());
    }

    private static AgentTickPreflightService.Hooks hooks() {
        return new AgentTickPreflightService.Hooks(
                AgentBotManagerStatusRuntime::airshowActive,
                AgentBotTickCadenceStateRuntime::consumeSkipDelay,
                AgentRuntimeCleanupService::removeAgentByCharacterId,
                (entry, agent, nowMs, heartbeatIntervalMs) -> AgentHeartbeatService.tickHeartbeat(
                        entry,
                        agent,
                        nowMs,
                        heartbeatIntervalMs,
                        heartbeatAgent -> heartbeatAgent.getClient().updateLastPacket(),
                        BotMovementManager::broadcastMovement),
                AgentOfferService::expirePendingOffer,
                AgentTickOrchestrator::prepareTick,
                BotMovementManager.configuredTickMs(),
                AgentRuntimeConfig.cfg.AI_TICK_MS,
                HEARTBEAT_INTERVAL_MS);
    }
}
