package server.agents.capabilities.supplies;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPotionRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentPotionServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void ownerPotionShareSchedulesThroughAgentPotionRuntime() {
        Character owner = mock(Character.class);
        Character requestingBot = mock(Character.class);
        Character donorBot = mock(Character.class);
        BotEntry entry = new BotEntry(requestingBot, owner, null);
        BotEntry donorEntry = new BotEntry(donorBot, owner, null);

        when(owner.getId()).thenReturn(88);
        when(owner.getMapId()).thenReturn(100000000);
        when(owner.getTrade()).thenReturn(null);
        when(donorBot.getMapId()).thenReturn(100000000);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(entry, donorEntry));

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class, CALLS_REAL_METHODS);
             MockedStatic<AgentBotPotionRuntime> scheduler = mockStatic(AgentBotPotionRuntime.class)) {
            potions.when(() -> AgentPotionService.countPotions(donorBot)).thenReturn(new int[]{400, 0});
            scheduler.when(() -> AgentBotPotionRuntime.randomDelayMs(900, 1400)).thenReturn(77L);

            assertEquals(AgentPotionService.OwnerPotShareResult.OFFERED,
                    AgentPotionService.offerPotShareToOwner(entry, true));

            scheduler.verify(() -> AgentBotPotionRuntime.randomDelayMs(900, 1400));
            scheduler.verify(() -> AgentBotPotionRuntime.afterDelay(eq(77L), any(Runnable.class)));
        } finally {
            bots.remove(owner.getId());
        }
    }
}
