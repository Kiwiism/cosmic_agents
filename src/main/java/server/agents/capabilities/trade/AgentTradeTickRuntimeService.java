package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.Trade;
import server.bots.BotEntry;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

public final class AgentTradeTickRuntimeService {
    private AgentTradeTickRuntimeService() {
    }

    public static void tickTrade(BotEntry entry,
                                 Character agent,
                                 RuntimeCallbacks callbacks,
                                 AgentInventoryTradeRuntimeService.RuntimeCallbacks inventoryCallbacks,
                                 AgentTradeLifecycleRuntimeService.RuntimeCallbacks lifecycleCallbacks) {
        AgentTradeTickService.tickTrade(
                entry,
                agent,
                AgentTradeTickCallbackService.tradeTickCallbacks(
                        callbacks.tickDown(),
                        () -> callbacks.currentTrade(agent),
                        () -> AgentTradeBetweenBatchService.tickBetweenBatches(
                                entry,
                                AgentTradeBetweenBatchCallbackService.betweenBatchCallbacks(
                                        callbacks.tickDown(),
                                        category -> collectItems(category, entry, agent, callbacks, inventoryCallbacks),
                                        category -> AgentTradeGroupNavigationService.nextEquipsGroup(
                                                category,
                                                () -> AgentInventoryTradeRuntimeService.classifyEquipTradeGroups(
                                                        agent,
                                                        inventoryCallbacks)),
                                        category -> AgentTradeGroupNavigationService.nextAmmoGroup(
                                                category,
                                                () -> AgentInventoryTradeRuntimeService.classifyAmmoTradeGroups(
                                                        agent,
                                                        inventoryCallbacks)),
                                        AgentInventoryTransferService::equipsGroupMessage,
                                        items -> AgentTradeSequenceRuntimeService.openTradeBatch(
                                                entry,
                                                agent,
                                                items,
                                                0,
                                                () -> AgentTradeLifecycleRuntimeService.cancelTradeSequence(
                                                        entry,
                                                        agent,
                                                        "can't trade right now, stopping",
                                                        lifecycleCallbacks)),
                                        () -> AgentTradeLifecycleRuntimeService.resetTradeState(entry, agent, lifecycleCallbacks))),
                        () -> AgentTradeClosedWindowService.handleClosedTrade(
                                entry,
                                () -> callbacks.delayAfterCurrentTick(1_000),
                                () -> AgentTradeLifecycleRuntimeService.resetTradeState(entry, agent, lifecycleCallbacks),
                                () -> callbacks.refillEquipment(agent, callbacks.owner(entry))),
                        trade -> AgentTradeInviteWaitService.tickWaitingForAccept(
                                entry,
                                agent,
                                callbacks.tickMs(),
                                () -> AgentTradeLifecycleRuntimeService.resetTradeState(entry, agent, lifecycleCallbacks)),
                        trade -> AgentTradeItemAddTickService.tickAddingItems(
                                entry,
                                agent,
                                trade,
                                AgentTradeItemAddTickCallbackService.itemAddTickCallbacks(
                                        callbacks.tickDown(),
                                        () -> AgentTradeLifecycleRuntimeService.cancelTradeSequence(
                                                entry,
                                                agent,
                                                "don't have that many mesos anymore",
                                                lifecycleCallbacks),
                                        () -> callbacks.delayAfterCurrentTick(500),
                                        AgentTradeDialogueService::allDoneReply,
                                        () -> callbacks.delayAfterCurrentTick(600),
                                        () -> callbacks.delayAfterCurrentTick(500))),
                        trade -> AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                                entry,
                                agent,
                                trade,
                                callbacks.tickMs(),
                                () -> callbacks.resolveTradeRecipient(entry, agent),
                                callbacks::isBotRecipient,
                                () -> AgentTradeLifecycleRuntimeService.completeTradeAndReact(
                                        entry,
                                        agent,
                                        trade,
                                        lifecycleCallbacks),
                                () -> AgentTradeLifecycleRuntimeService.resetTradeState(entry, agent, lifecycleCallbacks))));
    }

    private static List<Item> collectItems(String category,
                                           BotEntry entry,
                                           Character agent,
                                           RuntimeCallbacks callbacks,
                                           AgentInventoryTradeRuntimeService.RuntimeCallbacks inventoryCallbacks) {
        return AgentInventoryTradeRuntimeService.collectItems(
                category,
                agent,
                callbacks.owner(entry),
                inventoryCallbacks);
    }

    public interface RuntimeCallbacks {
        IntUnaryOperator tickDown();

        Trade currentTrade(Character agent);

        int delayAfterCurrentTick(int durationMs);

        int tickMs();

        Character owner(BotEntry entry);

        void refillEquipment(Character agent, Character owner);

        Character resolveTradeRecipient(BotEntry entry, Character agent);

        boolean isBotRecipient(Character recipient);

        static RuntimeCallbacks of(IntUnaryOperator tickDown,
                                   Function<Character, Trade> currentTrade,
                                   IntUnaryOperator delayAfterCurrentTick,
                                   java.util.function.IntSupplier tickMs,
                                   Function<BotEntry, Character> owner,
                                   BiConsumer<Character, Character> refillEquipment,
                                   TradeRecipientResolver resolveTradeRecipient,
                                   java.util.function.Predicate<Character> isBotRecipient) {
            return new RuntimeCallbacks() {
                @Override
                public IntUnaryOperator tickDown() {
                    return tickDown;
                }

                @Override
                public Trade currentTrade(Character agent) {
                    return currentTrade.apply(agent);
                }

                @Override
                public int delayAfterCurrentTick(int durationMs) {
                    return delayAfterCurrentTick.applyAsInt(durationMs);
                }

                @Override
                public int tickMs() {
                    return tickMs.getAsInt();
                }

                @Override
                public Character owner(BotEntry entry) {
                    return owner.apply(entry);
                }

                @Override
                public void refillEquipment(Character agent, Character owner) {
                    refillEquipment.accept(agent, owner);
                }

                @Override
                public Character resolveTradeRecipient(BotEntry entry, Character agent) {
                    return resolveTradeRecipient.resolve(entry, agent);
                }

                @Override
                public boolean isBotRecipient(Character recipient) {
                    return isBotRecipient.test(recipient);
                }
            };
        }
    }

    @FunctionalInterface
    public interface TradeRecipientResolver {
        Character resolve(BotEntry entry, Character agent);
    }
}
