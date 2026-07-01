package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.trade.AgentTradeItemAddTickService.ItemAddTickCallbacks;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeItemAddTickCallbackServiceTest {
    @Test
    void buildsItemAddTickCallbacksFromLegacyOperations() {
        AtomicBoolean cancelled = new AtomicBoolean();

        ItemAddTickCallbacks callbacks = AgentTradeItemAddTickCallbackService.itemAddTickCallbacks(
                value -> value - 1,
                () -> cancelled.set(true),
                () -> 500,
                () -> "done",
                () -> 600,
                () -> 700);

        assertEquals(4, callbacks.tickDown().applyAsInt(5));
        callbacks.insufficientMesoCancel().run();
        assertEquals(500, callbacks.mesoAddDelayMs().getAsInt());
        assertEquals("done", callbacks.allDoneReply().get());
        assertEquals(600, callbacks.categoryAnnouncementDelayMs().getAsInt());
        assertEquals(700, callbacks.itemAddDelayMs().getAsInt());
        assertTrue(cancelled.get());
    }
}
