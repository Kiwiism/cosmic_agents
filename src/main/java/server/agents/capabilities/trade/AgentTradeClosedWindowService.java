package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntSupplier;

public final class AgentTradeClosedWindowService {
    private AgentTradeClosedWindowService() {
    }

    public static boolean handleClosedTrade(AgentRuntimeEntry entry,
                                            IntSupplier betweenBatchDelayMs,
                                            Runnable resetTradeState,
                                            Runnable refillEquipmentSlots) {
        if (AgentPendingTradeStateRuntime.botDone(entry)) {
            if (AgentPendingTradeStateRuntime.singleBatch(entry)) {
                resetTradeState.run();
                refillEquipmentSlots.run();
                return true;
            }
            AgentTradeStateService.enterBetweenBatches(entry, betweenBatchDelayMs.getAsInt());
            return true;
        }

        if (AgentPendingTradeStateRuntime.allItemsAdded(entry)) {
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
