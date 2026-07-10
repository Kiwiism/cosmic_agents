package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentTradeCompletionServiceTest {
    @Test
    void receivedTradeSnapshotsOwnerGivenEquipsCompletesAndThanks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Item equip = new Item(1002000, (short) 1, (short) 1);
        AtomicBoolean delayedReply = new AtomicBoolean(false);

        try (MockedStatic<Trade> tradeStatic = mockStatic(Trade.class);
             MockedStatic<AgentInventoryRuntime> inventory = mockStatic(AgentInventoryRuntime.class)) {
            inventory.when(() -> AgentInventoryRuntime.afterDelay(org.mockito.ArgumentMatchers.eq(entry), org.mockito.ArgumentMatchers.eq(900L), org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        delayedReply.set(true);
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentTradeCompletionService.completeAndReact(
                    entry,
                    agent,
                    List.of(equip),
                    true,
                    () -> 900L,
                    () -> "thanks",
                    () -> "freebie",
                    () -> 99,
                    () -> true);

            assertTrue(AgentPendingTradeStateRuntime.hasOwnerGivenItems(entry));
            verify(agent).changeFaceExpression(AgentEmote.HAPPY.getValue());
            tradeStatic.verify(() -> Trade.completeTrade(agent));
            inventory.verify(() -> AgentInventoryRuntime.visibleSayNow(entry, "thanks"));
            assertTrue(delayedReply.get());
        }
    }

    @Test
    void emptyTradeMaySendFreebieReaction() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);

        try (MockedStatic<Trade> tradeStatic = mockStatic(Trade.class);
             MockedStatic<AgentInventoryRuntime> inventory = mockStatic(AgentInventoryRuntime.class)) {
            inventory.when(() -> AgentInventoryRuntime.afterDelay(org.mockito.ArgumentMatchers.eq(entry), org.mockito.ArgumentMatchers.eq(800L), org.mockito.ArgumentMatchers.any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentTradeCompletionService.completeAndReact(
                    entry,
                    agent,
                    List.of(),
                    false,
                    () -> 800L,
                    () -> "thanks",
                    () -> "freebie",
                    () -> 0,
                    () -> false);

            assertFalse(AgentPendingTradeStateRuntime.hasOwnerGivenItems(entry));
            verify(agent).changeFaceExpression(AgentEmote.ANNOYED.getValue());
            tradeStatic.verify(() -> Trade.completeTrade(agent));
            inventory.verify(() -> AgentInventoryRuntime.visibleSayNow(entry, "freebie"));
        }
    }
}
