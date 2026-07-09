package server.agents.capabilities.trade;

import client.Character;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentManualTradeCallbackService {
    private AgentManualTradeCallbackService() {
    }

    public static AgentManualTradeTickService.ManualTradeTickCallbacks manualTradeTickCallbacks(
            BooleanSupplier hasActiveSequence,
            Function<Character, AgentTradeWindow> agentTrade,
            Consumer<Character> clearState,
            BiPredicate<Character, AgentTradeWindow> beginOrTickTimeout,
            Function<Character, AgentTradeWindow> ownerTrade,
            AgentManualTradeTickService.PeerTradeTicker tickPeerTrade,
            AgentManualTradeTickService.OwnerTradeTicker tickOwnerTrade) {
        return AgentManualTradeTickService.ManualTradeTickCallbacks.of(
                hasActiveSequence,
                agentTrade,
                clearState,
                beginOrTickTimeout,
                ownerTrade,
                tickPeerTrade,
                tickOwnerTrade);
    }

    public static AgentManualPeerTradeService.PeerTradeCallbacks peerTradeCallbacks(
            Predicate<Character> isPeerAgent,
            BiPredicate<Integer, Integer> isAuthorizedPeer,
            BiFunction<Character, AgentTradeWindow, AgentTradeWindow> acceptInvite,
            Consumer<AgentTradeWindow> completeTrade,
            Consumer<Character> refillEquipment,
            Consumer<Character> clearGreeting) {
        return AgentManualPeerTradeService.PeerTradeCallbacks.of(
                isPeerAgent,
                isAuthorizedPeer,
                acceptInvite,
                completeTrade,
                refillEquipment,
                clearGreeting);
    }

    public static AgentManualOwnerTradeService.OwnerTradeCallbacks ownerTradeCallbacks(
            BiFunction<Character, AgentTradeWindow, AgentTradeWindow> acceptInvite,
            AgentManualOwnerTradeService.GreetingSender sendGreeting,
            Supplier<String> manualTradeGreeting,
            Consumer<AgentTradeWindow> completeTrade,
            Consumer<Character> refillEquipment) {
        return AgentManualOwnerTradeService.OwnerTradeCallbacks.of(
                acceptInvite,
                sendGreeting,
                manualTradeGreeting,
                completeTrade,
                refillEquipment);
    }
}
