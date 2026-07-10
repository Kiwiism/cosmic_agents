package server.agents.capabilities.trade;

import client.BotClient;
import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.TimerManager;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOwnerItemNotificationServiceTest {
    @Test
    void skipsOfferScanForItemsTradedFromOwnedAgent() {
        Character owner = mock(Character.class);
        Character sourceAgent = mock(Character.class);
        Character observerAgent = mock(Character.class);
        Item tradedEquip = new Item(1002000, (short) 1, (short) 1);

        when(owner.getId()).thenReturn(77);
        when(sourceAgent.getId()).thenReturn(10);
        when(sourceAgent.getClient()).thenReturn(new BotClient(0, 0));

        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(owner.getId(), List.of(
                new AgentRuntimeEntry(sourceAgent, owner, null),
                new AgentRuntimeEntry(observerAgent, owner, null)));

        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            AgentOwnerItemNotificationService.notifyOwnerGainedTradeItem(owner, tradedEquip, sourceAgent);

            offers.verifyNoInteractions();
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    @Test
    void notifiesAgentsForNonOwnAgentTradeItems() {
        Character owner = mock(Character.class);
        Character observerAgent = mock(Character.class);
        Character sourcePlayer = mock(Character.class);
        AgentRuntimeEntry observerEntry = new AgentRuntimeEntry(observerAgent, owner, null);
        Item tradedEquip = new Item(1002000, (short) 1, (short) 1);
        TimerManager inlineTimer = mock(TimerManager.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);

        when(owner.getId()).thenReturn(78);
        when(inlineTimer.schedule(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return scheduledFuture;
        });

        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(owner.getId(), List.of(observerEntry));

        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class);
             MockedStatic<TimerManager> timer = mockStatic(TimerManager.class)) {
            timer.when(TimerManager::getInstance).thenReturn(inlineTimer);

            AgentOwnerItemNotificationService.notifyOwnerGainedTradeItem(owner, tradedEquip, sourcePlayer);

            offers.verify(() -> AgentOfferService.notifyOwnerGainedEquip(observerEntry, observerAgent, tradedEquip));
            verify(inlineTimer).schedule(any(Runnable.class), anyLong());
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }
}
