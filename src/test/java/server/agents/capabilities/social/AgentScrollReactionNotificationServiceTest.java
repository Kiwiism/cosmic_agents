package server.agents.capabilities.social;

import client.Character;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.TimerManager;
import server.agents.runtime.AgentRuntimeRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScrollReactionNotificationServiceTest {
    @Test
    void schedulesScrollReactionForwardingThroughAgentRegistry() {
        Character source = mock(Character.class);
        TimerManager inlineTimer = mock(TimerManager.class);
        when(inlineTimer.schedule(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });

        try (MockedStatic<TimerManager> timer = mockStatic(TimerManager.class);
             MockedStatic<AgentScrollReactionService> reactions = mockStatic(AgentScrollReactionService.class)) {
            timer.when(TimerManager::getInstance).thenReturn(inlineTimer);

            AgentScrollReactionNotificationService.notifyNearbyAgentsOfScroll(
                    source, Equip.ScrollResult.SUCCESS, 2040001, -1L);

            verify(inlineTimer).schedule(any(Runnable.class), eq(0L));
            reactions.verify(() -> AgentScrollReactionService.handleScrollEvent(
                    source,
                    Equip.ScrollResult.SUCCESS,
                    2040001,
                    AgentRuntimeRegistry.entriesByLeaderId().values()));
        }
    }
}
