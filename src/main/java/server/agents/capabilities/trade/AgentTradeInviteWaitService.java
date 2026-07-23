package server.agents.capabilities.trade;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeInviteWaitService {
    private static final int REQUEST_TIMEOUT_MS = config.AgentTuning.intValue("server.agents.capabilities.trade.AgentTradeInviteWaitService.REQUEST_TIMEOUT_MS");

    private AgentTradeInviteWaitService() {
    }

    public static void tickWaitingForAccept(AgentRuntimeEntry entry,
                                            Character agent,
                                            int tickMs,
                                            Runnable resetTradeState) {
        AgentPendingTradeStateRuntime.addTimerMs(entry, tickMs);
        if (AgentPendingTradeStateRuntime.timerMs(entry) > REQUEST_TIMEOUT_MS) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRequestTimeoutReply());
            AgentTradeGatewayRuntime.trade().cancelNoResponse(agent);
            resetTradeState.run();
        }
    }
}
