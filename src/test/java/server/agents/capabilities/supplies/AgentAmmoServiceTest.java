package server.agents.capabilities.supplies;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.supplies.AgentAmmoRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import testutil.Items;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentAmmoServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void ownerAmmoShareSchedulesThroughAgentAmmoRuntime() {
        Character owner = mock(Character.class);
        Character requestingBot = mock(Character.class);
        Character donorBot = ammoBot(22, 100000000, 1000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(requestingBot, owner, null);
        AgentRuntimeEntry donorEntry = new AgentRuntimeEntry(donorBot, owner, null);

        when(owner.getId()).thenReturn(77);
        when(owner.getMapId()).thenReturn(100000000);
        when(owner.getTrade()).thenReturn(null);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(entry, donorEntry));

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentAmmoRuntime> scheduler = mockStatic(AgentAmmoRuntime.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(donorBot)).thenReturn(null);
            scheduler.when(() -> AgentAmmoRuntime.randomDelayMs(900, 1400)).thenReturn(99L);

            assertEquals(AgentAmmoService.OwnerAmmoShareResult.OFFERED,
                    AgentAmmoService.offerAmmoShareToOwner(entry, WeaponType.BOW, mock(InventoryGateway.class)));

            scheduler.verify(() -> AgentAmmoRuntime.randomDelayMs(900, 1400));
            scheduler.verify(() -> AgentAmmoRuntime.afterDelay(eq(99L), any(Runnable.class)));
        } finally {
            bots.remove(owner.getId());
        }
    }

    private static Character ammoBot(int id, int mapId, int arrows) {
        Character bot = mock(Character.class);
        Inventory use = new Inventory(bot, InventoryType.USE, (byte) 24);
        use.addItem(Items.itemWithQuantity(2060000, arrows));
        when(bot.getId()).thenReturn(id);
        when(bot.getMapId()).thenReturn(mapId);
        when(bot.getInventory(InventoryType.USE)).thenReturn(use);
        return bot;
    }
}
