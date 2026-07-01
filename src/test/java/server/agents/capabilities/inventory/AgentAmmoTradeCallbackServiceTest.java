package server.agents.capabilities.inventory;

import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentAmmoTradeClassificationService.AmmoTradeCallbacks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAmmoTradeCallbackServiceTest {
    @Test
    void buildsAmmoTradeCallbacksFromLegacyOperations() {
        AmmoTradeCallbacks callbacks = AgentAmmoTradeCallbackService.ammoTradeCallbacks(
                () -> WeaponType.BOW,
                itemId -> itemId + 1,
                itemId -> itemId == 4000000,
                () -> false);

        assertEquals(WeaponType.BOW, callbacks.equippedWeaponType());
        assertEquals(2060001, callbacks.projectileWatk().applyAsInt(2060000));
        assertTrue(callbacks.isQuestItem().test(4000000));
        assertFalse(callbacks.untradeableItemsTradeable());
    }
}
