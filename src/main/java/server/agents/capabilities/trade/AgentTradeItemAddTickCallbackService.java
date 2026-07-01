package server.agents.capabilities.trade;

import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public final class AgentTradeItemAddTickCallbackService {
    private AgentTradeItemAddTickCallbackService() {
    }

    public static AgentTradeItemAddTickService.ItemAddTickCallbacks itemAddTickCallbacks(
            IntUnaryOperator tickDown,
            Runnable insufficientMesoCancel,
            IntSupplier mesoAddDelayMs,
            Supplier<String> allDoneReply,
            IntSupplier categoryAnnouncementDelayMs,
            IntSupplier itemAddDelayMs) {
        return AgentTradeItemAddTickService.ItemAddTickCallbacks.of(
                tickDown,
                insufficientMesoCancel,
                mesoAddDelayMs,
                allDoneReply,
                categoryAnnouncementDelayMs,
                itemAddDelayMs);
    }
}
