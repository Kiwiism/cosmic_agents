package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeInviteWaitService {
    private static final int REQUEST_TIMEOUT_MS = 30_000;

    private AgentTradeInviteWaitService() {
    }

    public static void tickWaitingForAccept(AgentRuntimeEntry entry,
                                            Character agent,
                                            int tickMs,
                                            Runnable resetTradeState) {
        AgentPendingTradeStateRuntime.addTimerMs(entry, tickMs);
        if (AgentPendingTradeStateRuntime.timerMs(entry) > REQUEST_TIMEOUT_MS) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRequestTimeoutReply());
            Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE);
            resetTradeState.run();
        }
    }
}
