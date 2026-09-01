package server.agents.capabilities.supplies;

import client.Character;
import client.Disease;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.supplies.AgentPotionRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.InventoryGateway;
import server.StatEffect;
import server.life.MobSkill;
import tools.Pair;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentPotionServiceTest {
    @Test
    void consumesAMatchingDiseaseCureOnlyAfterTheRolledDelay() {
        Character bot = mock(Character.class);
        Inventory useInventory = mock(Inventory.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Item holyWater = mock(Item.class);
        StatEffect effect = mock(StatEffect.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        when(bot.getAllDiseases()).thenReturn(Map.of(
                Disease.SEAL, new Pair<>(30_000L, mock(MobSkill.class))));
        when(bot.getInventory(InventoryType.USE)).thenReturn(useInventory);
        when(useInventory.list()).thenReturn(List.of(holyWater));
        when(holyWater.getItemId()).thenReturn(2_050_003);
        when(holyWater.getPosition()).thenReturn((short) 4);
        when(holyWater.getQuantity()).thenReturn((short) 1);
        when(inventory.getItemEffect(2_050_003)).thenReturn(effect);
        when(effect.curesDisease(Disease.SEAL)).thenReturn(true);
        when(inventory.consumeUseItem(bot, (short) 4, 2_050_003)).thenReturn(true);

        AgentPotionService.tickDiseaseCure(entry, bot, inventory, 1_000L, () -> 1_500L);
        AgentPotionService.tickDiseaseCure(entry, bot, inventory, 2_499L, () -> 1_500L);

        verify(inventory, never()).consumeUseItem(bot, (short) 4, 2_050_003);
        AgentPotionService.tickDiseaseCure(entry, bot, inventory, 2_500L, () -> 1_500L);
        verify(inventory).consumeUseItem(bot, (short) 4, 2_050_003);
        assertEquals(0L, AgentPotionStateRuntime.diseaseCureDueAtMs(entry));
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
        AgentRuntimeRegistry.registerEntry(owner.getId(), entry);
        AgentRuntimeRegistry.registerEntry(owner.getId(), donorEntry);

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class, CALLS_REAL_METHODS);
             MockedStatic<AgentPotionRuntime> scheduler = mockStatic(AgentPotionRuntime.class)) {
            potions.when(() -> AgentPotionService.countPotions(donorBot)).thenReturn(new int[]{400, 0});
            scheduler.when(() -> AgentPotionRuntime.randomDelayMs(900, 1400)).thenReturn(77L);

            assertEquals(AgentPotionService.OwnerPotShareResult.OFFERED,
                    AgentPotionService.offerPotShareToOwner(entry, true));

            scheduler.verify(() -> AgentPotionRuntime.randomDelayMs(900, 1400));
            scheduler.verify(() -> AgentPotionRuntime.afterDelay(eq(donorEntry), eq(77L), any(Runnable.class)));
        } finally {
            AgentRuntimeRegistry.unregisterLeader(owner.getId());
        }
    }
}
