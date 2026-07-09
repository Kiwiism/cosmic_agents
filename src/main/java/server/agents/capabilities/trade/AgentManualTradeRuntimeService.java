package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentManualTradeRuntimeService {
    private AgentManualTradeRuntimeService() {
    }

    public static void tickManualTrade(AgentRuntimeEntry entry,
                                       Character agent,
                                       Character owner,
                                       RuntimeCallbacks callbacks,
                                       AgentTradeLifecycleService.LifecycleCallbacks lifecycleCallbacks) {
        AgentManualTradeTickService.tickManualTrade(
                agent,
                owner,
                AgentManualTradeCallbackService.manualTradeTickCallbacks(
                        () -> callbacks.hasActiveSequence(entry),
                        tradeOwner -> AgentTradeGatewayRuntime.trade().currentWindow(tradeOwner),
                        clearAgent -> AgentManualTradeService.clearState(entry, clearAgent),
                        (tradeAgent, trade) -> AgentManualTradeService.beginOrTickTimeout(
                                entry,
                                tradeAgent,
                                trade,
                                callbacks::tickDown),
                        tradeOwner -> AgentTradeGatewayRuntime.trade().currentWindow(tradeOwner),
                        (tradeAgent, tradeOwner, trade, isOwnerTrade) -> AgentManualPeerTradeService.tickPeerTrade(
                                entry,
                                tradeAgent,
                                tradeOwner,
                                trade,
                                isOwnerTrade,
                                AgentManualTradeCallbackService.peerTradeCallbacks(
                                        callbacks::isPeerAgent,
                                        callbacks::isAuthorizedPeer,
                                        (inviter, pendingTrade) -> AgentManualTradeService.acceptInviteWhenReady(
                                                entry,
                                                tradeAgent,
                                                inviter,
                                                pendingTrade,
                                                500 + callbacks.tickMs(),
                                                callbacks::tickDown,
                                                currentOwner -> AgentTradeGatewayRuntime.trade().currentWindow(currentOwner)),
                                        completedTrade -> AgentTradeLifecycleService.completeTradeAndReact(
                                                entry,
                                                tradeAgent,
                                                completedTrade,
                                                lifecycleCallbacks),
                                        peerOwner -> callbacks.refillEquipment(tradeAgent, peerOwner),
                                        AgentManualTradeService::clearGreeting)),
                        (tradeAgent, tradeOwner, trade) -> AgentManualOwnerTradeService.tickOwnerTrade(
                                tradeAgent,
                                tradeOwner,
                                trade,
                                AgentManualTradeCallbackService.ownerTradeCallbacks(
                                        (inviter, pendingTrade) -> AgentManualTradeService.acceptInviteWhenReady(
                                                entry,
                                                tradeAgent,
                                                inviter,
                                                pendingTrade,
                                                500 + callbacks.tickMs(),
                                                callbacks::tickDown,
                                                currentOwner -> AgentTradeGatewayRuntime.trade().currentWindow(currentOwner)),
                                        AgentManualTradeService::sendGreetingOnce,
                                        callbacks::manualTradeGreeting,
                                        completedTrade -> AgentTradeLifecycleService.completeTradeAndReact(
                                                entry,
                                                tradeAgent,
                                                completedTrade,
                                                lifecycleCallbacks),
                                        refillOwner -> callbacks.refillEquipment(tradeAgent, refillOwner)))));
    }

    public interface RuntimeCallbacks {
        boolean hasActiveSequence(AgentRuntimeEntry entry);

        int tickDown(int delayMs);

        int tickMs();

        boolean isPeerAgent(Character peer);

        boolean isAuthorizedPeer(int peerId, int ownerId);

        String manualTradeGreeting();

        void refillEquipment(Character agent, Character owner);

        static RuntimeCallbacks of(BooleanSupplier hasActiveSequence,
                                   IntUnaryOperator tickDown,
                                   IntSupplier tickMs,
                                   Predicate<Character> isPeerAgent,
                                   BiPredicate<Integer, Integer> isAuthorizedPeer,
                                   Supplier<String> manualTradeGreeting,
                                   BiConsumer<Character, Character> refillEquipment) {
            return new RuntimeCallbacks() {
                @Override
                public boolean hasActiveSequence(AgentRuntimeEntry entry) {
                    return hasActiveSequence.getAsBoolean();
                }

                @Override
                public int tickDown(int delayMs) {
                    return tickDown.applyAsInt(delayMs);
                }

                @Override
                public int tickMs() {
                    return tickMs.getAsInt();
                }

                @Override
                public boolean isPeerAgent(Character peer) {
                    return isPeerAgent.test(peer);
                }

                @Override
                public boolean isAuthorizedPeer(int peerId, int ownerId) {
                    return isAuthorizedPeer.test(peerId, ownerId);
                }

                @Override
                public String manualTradeGreeting() {
                    return manualTradeGreeting.get();
                }

                @Override
                public void refillEquipment(Character agent, Character owner) {
                    refillEquipment.accept(agent, owner);
                }
            };
        }
    }
}
