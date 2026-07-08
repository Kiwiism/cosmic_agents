package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.Trade;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public final class AgentTradeItemAddTickService {
    private AgentTradeItemAddTickService() {
    }

    public static boolean tickAddingItems(AgentRuntimeEntry entry,
                                          Character agent,
                                          Trade trade,
                                          ItemAddTickCallbacks callbacks) {
        if (AgentPendingTradeStateRuntime.allItemsAdded(entry)) {
            return false;
        }
        if (AgentPendingTradeStateRuntime.timerMs(entry) > 0) {
            AgentPendingTradeStateRuntime.tickTimerDown(entry, callbacks.tickDown());
            return true;
        }

        if (AgentTradeMesoAddService.handlePendingMeso(
                entry,
                agent,
                trade,
                callbacks.insufficientMesoCancel(),
                callbacks.mesoAddDelayMs())) {
            return true;
        }

        List<Item> items = AgentPendingTradeStateRuntime.items(entry);
        int idx = AgentPendingTradeStateRuntime.itemIndex(entry);

        if (idx >= items.size()) {
            AgentTradeAllItemsAddedService.markCompleteIfNoMoreItems(entry, trade, callbacks.allDoneReply());
            return true;
        }

        if (AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(
                entry,
                trade,
                callbacks.categoryAnnouncementDelayMs())) {
            return true;
        }

        AgentTradeItemAddService.addNextItem(entry, agent, trade, callbacks.itemAddDelayMs().getAsInt());
        return true;
    }

    public interface ItemAddTickCallbacks {
        IntUnaryOperator tickDown();
        Runnable insufficientMesoCancel();
        IntSupplier mesoAddDelayMs();
        Supplier<String> allDoneReply();
        IntSupplier categoryAnnouncementDelayMs();
        IntSupplier itemAddDelayMs();

        static ItemAddTickCallbacks of(IntUnaryOperator tickDown,
                                       Runnable insufficientMesoCancel,
                                       IntSupplier mesoAddDelayMs,
                                       Supplier<String> allDoneReply,
                                       IntSupplier categoryAnnouncementDelayMs,
                                       IntSupplier itemAddDelayMs) {
            return new ItemAddTickCallbacks() {
                @Override public IntUnaryOperator tickDown() { return tickDown; }
                @Override public Runnable insufficientMesoCancel() { return insufficientMesoCancel; }
                @Override public IntSupplier mesoAddDelayMs() { return mesoAddDelayMs; }
                @Override public Supplier<String> allDoneReply() { return allDoneReply; }
                @Override public IntSupplier categoryAnnouncementDelayMs() { return categoryAnnouncementDelayMs; }
                @Override public IntSupplier itemAddDelayMs() { return itemAddDelayMs; }
            };
        }
    }
}
