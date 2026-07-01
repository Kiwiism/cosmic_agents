package server.agents.capabilities.trade;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class AgentTradeLifecycleCallbackService {
    private AgentTradeLifecycleCallbackService() {
    }

    public static AgentTradeLifecycleService.LifecycleCallbacks lifecycleCallbacks(
            AgentTradeLifecycleService.RestoreSlots restoreTemporarilyUnequippedItems,
            AgentTradeLifecycleService.ClearManualTrade clearManualTradeState,
            AgentTradeLifecycleService.OwnerLookup owner,
            AgentTradeLifecycleService.RefillEquipment refillEquipmentSlots,
            AgentTradeLifecycleService.ReplyDelay randomReplyDelayMs,
            Supplier<String> tradeThanksReply,
            Supplier<String> tradeFreebieReply,
            IntSupplier freebieRoll,
            BooleanSupplier glareExpression) {
        return AgentTradeLifecycleService.LifecycleCallbacks.of(
                restoreTemporarilyUnequippedItems,
                clearManualTradeState,
                owner,
                refillEquipmentSlots,
                randomReplyDelayMs,
                tradeThanksReply,
                tradeFreebieReply,
                freebieRoll,
                glareExpression);
    }
}
