package server.agents.capabilities.trade;

import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentTradeTransferRouter {
    private AgentTradeTransferRouter() {
    }

    public static void routeCategoryTransfer(String category,
                                             boolean ownerPresent,
                                             boolean agentBusy,
                                             boolean ownerBusy,
                                             long startedAt,
                                             TransferCallbacks callbacks) {
        if (AgentInventoryTradePolicy.isMesoCategory(category)) {
            callbacks.startMesoTransfer();
            return;
        }

        AgentTradeTransferStartGuard.Decision startDecision =
                AgentTradeTransferStartGuard.evaluate(ownerPresent, agentBusy, ownerBusy);
        if (!startDecision.proceed()) {
            callbacks.reply(startDecision.reply());
            return;
        }

        if ("equips".equals(category)) {
            long branchStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
            callbacks.startEquipsGroupTransfer();
            callbacks.logSlowCommand("startEquipsGroupTradeTransfer", branchStartedAt);
            callbacks.logSlowCommand("startTradeTransfer", startedAt);
            return;
        }

        if (AgentInventoryTradePolicy.isReservedEquipsCategory(category)) {
            callbacks.startReservedEquipTransfer();
            callbacks.logSlowCommand("startTradeTransfer", startedAt);
            return;
        }

        if ("ammo".equals(category)) {
            callbacks.startAmmoGroupTransfer();
            callbacks.logSlowCommand("startTradeTransfer", startedAt);
            return;
        }

        long prepareStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        PreparedTradeItems prepared = callbacks.prepareTradeItems();
        callbacks.logSlowCommand("prepareTradeItems", prepareStartedAt);
        callbacks.startPreparedTransfer(prepared);
        callbacks.logSlowCommand("startTradeTransfer", startedAt);
    }

    public interface TransferCallbacks {
        void startMesoTransfer();
        void startEquipsGroupTransfer();
        void startReservedEquipTransfer();
        void startAmmoGroupTransfer();
        PreparedTradeItems prepareTradeItems();
        void startPreparedTransfer(PreparedTradeItems prepared);
        void reply(String message);
        void logSlowCommand(String operation, long startedAt);

        static TransferCallbacks of(Runnable startMesoTransfer,
                                    Runnable startEquipsGroupTransfer,
                                    Runnable startReservedEquipTransfer,
                                    Runnable startAmmoGroupTransfer,
                                    Supplier<PreparedTradeItems> prepareTradeItems,
                                    Consumer<PreparedTradeItems> startPreparedTransfer,
                                    Consumer<String> reply,
                                    java.util.function.BiConsumer<String, Long> logSlowCommand) {
            return new TransferCallbacks() {
                @Override public void startMesoTransfer() { startMesoTransfer.run(); }
                @Override public void startEquipsGroupTransfer() { startEquipsGroupTransfer.run(); }
                @Override public void startReservedEquipTransfer() { startReservedEquipTransfer.run(); }
                @Override public void startAmmoGroupTransfer() { startAmmoGroupTransfer.run(); }
                @Override public PreparedTradeItems prepareTradeItems() { return prepareTradeItems.get(); }
                @Override public void startPreparedTransfer(PreparedTradeItems prepared) { startPreparedTransfer.accept(prepared); }
                @Override public void reply(String message) { reply.accept(message); }
                @Override public void logSlowCommand(String operation, long startedAt) {
                    logSlowCommand.accept(operation, startedAt);
                }
            };
        }
    }
}
