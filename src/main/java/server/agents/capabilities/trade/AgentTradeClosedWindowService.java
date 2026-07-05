package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntSupplier;

public final class AgentTradeClosedWindowService {
    private AgentTradeClosedWindowService() {
    }

    public static boolean handleClosedTrade(AgentRuntimeEntry entry,
                                            IntSupplier betweenBatchDelayMs,
                                            Runnable resetTradeState,
                                            Runnable refillEquipmentSlots) {
        if (AgentBotPendingTradeStateRuntime.botDone(entry)) {
            if (AgentBotPendingTradeStateRuntime.singleBatch(entry)) {
                resetTradeState.run();
                refillEquipmentSlots.run();
                return true;
            }
            AgentTradeStateService.enterBetweenBatches(entry, betweenBatchDelayMs.getAsInt());
            return true;
        }

        if (AgentBotPendingTradeStateRuntime.allItemsAdded(entry)) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeCancelledReply());
            resetTradeState.run();
            refillEquipmentSlots.run();
            return true;
        }

        AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeDeclinedReply());
        resetTradeState.run();
        return true;
    }
}
