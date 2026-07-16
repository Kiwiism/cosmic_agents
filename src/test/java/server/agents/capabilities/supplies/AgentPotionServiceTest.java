package server.agents.capabilities.supplies;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.InventoryGateway;
import server.agents.capabilities.supplies.AgentPotionRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentPotionServiceTest {
    @Test
    void partnerRefreshesSurvivalAutopotWithoutEnteringSupplyManagement() {
        Character owner = mock(Character.class);
        Character partner = mock(Character.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(partner, owner, null);
        entry.markPartnerManaged();

        try (MockedStatic<AgentPotionService> potions =
                     mockStatic(AgentPotionService.class, CALLS_REAL_METHODS)) {
            potions.when(() -> AgentPotionService.setupAutopotForBot(partner))
                    .thenAnswer(ignored -> null);
            potions.clearInvocations();

            AgentPotionService.tickPotionCheck(entry, partner, inventory);

            potions.verify(() -> AgentPotionService.setupAutopotForBot(partner));
            verifyNoInteractions(inventory);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerPotionShareSchedulesThroughAgentPotionRuntime() {
        Character owner = mock(Character.class);
        Character requestingBot = mock(Character.class);
        Character donorBot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(requestingBot, owner, null);
        AgentRuntimeEntry donorEntry = new AgentRuntimeEntry(donorBot, owner, null);

        when(owner.getId()).thenReturn(88);
        when(owner.getMapId()).thenReturn(100000000);
        when(owner.getTrade()).thenReturn(null);
        when(donorBot.getMapId()).thenReturn(100000000);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(entry, donorEntry));

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class, CALLS_REAL_METHODS);
             MockedStatic<AgentPotionRuntime> scheduler = mockStatic(AgentPotionRuntime.class)) {
            potions.when(() -> AgentPotionService.countPotions(donorBot)).thenReturn(new int[]{400, 0});
            scheduler.when(() -> AgentPotionRuntime.randomDelayMs(900, 1400)).thenReturn(77L);

            assertEquals(AgentPotionService.OwnerPotShareResult.OFFERED,
                    AgentPotionService.offerPotShareToOwner(entry, true));

            scheduler.verify(() -> AgentPotionRuntime.randomDelayMs(900, 1400));
            scheduler.verify(() -> AgentPotionRuntime.afterDelay(eq(donorEntry), eq(77L), any(Runnable.class)));
        } finally {
            bots.remove(owner.getId());
        }
    }
}
