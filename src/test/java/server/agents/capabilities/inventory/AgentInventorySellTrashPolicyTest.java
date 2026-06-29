package server.agents.capabilities.inventory;

import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventorySellTrashPolicyTest {
    @Test
    void shouldProtectSellTrashEquipsUsingLegacyStatRules() {
        Equip sellable = equip(1000001);
        Equip highIntAllJob = equip(1082001);
        Equip highDexWarrior = equip(1000002);
        Equip pirateDex = equip(1050001);
        Equip pureHighDex = equip(1000003);

        when(sellable.getStr()).thenReturn((short) 3);
        when(highIntAllJob.getInt()).thenReturn((short) 6);
        when(highDexWarrior.getDex()).thenReturn((short) 6);
        when(pirateDex.getDex()).thenReturn((short) 6);
        when(pureHighDex.getDex()).thenReturn((short) 10);

        assertFalse(AgentInventorySellTrashPolicy.hasProtectedSellTrashStat(
                Map.of("reqJob", 1), sellable, 6, 10));
        assertTrue(AgentInventorySellTrashPolicy.hasProtectedSellTrashStat(
                Map.of("reqJob", 0), highIntAllJob, 6, 10));
        assertTrue(AgentInventorySellTrashPolicy.hasProtectedSellTrashStat(
                Map.of("reqJob", 1), highDexWarrior, 6, 10));
        assertTrue(AgentInventorySellTrashPolicy.hasProtectedSellTrashStat(
                Map.of("reqJob", 16), pirateDex, 6, 10));
        assertFalse(AgentInventorySellTrashPolicy.hasProtectedSellTrashStat(
                Map.of("reqJob", 1, "DEX", 6), highDexWarrior, 6, 10));
        assertTrue(AgentInventorySellTrashPolicy.hasProtectedSellTrashStat(
                Map.of("reqJob", 1, "DEX", 10), pureHighDex, 6, 10));
    }

    @Test
    void shouldProtectScrolledAndNonWeaponWatkEquipsForSellTrash() {
        Equip nonWeaponWatk = equip(1072001);
        Equip scrolled = equip(1040001);
        when(nonWeaponWatk.getWatk()).thenReturn((short) 1);
        when(scrolled.getLevel()).thenReturn((byte) 1);

        assertTrue(AgentInventorySellTrashPolicy.shouldKeepForSellTrash(null, nonWeaponWatk));
        assertTrue(AgentInventorySellTrashPolicy.shouldKeepForSellTrash(null, scrolled));
    }

    @Test
    void shouldProtectWeaponStatsUsingLegacyAttackThresholds() {
        Equip currentWarriorWeapon = equip(1302000);
        Equip baseWarriorWeapon = equip(1302000);
        Equip currentMageWeapon = equip(1372000);
        Equip baseMageWeapon = equip(1372000);
        when(currentWarriorWeapon.getWatk()).thenReturn((short) 24);
        when(baseWarriorWeapon.getWatk()).thenReturn((short) 20);
        when(currentMageWeapon.getMatk()).thenReturn((short) 29);
        when(baseMageWeapon.getMatk()).thenReturn((short) 25);

        assertTrue(AgentInventorySellTrashPolicy.hasProtectedSellTrashWeaponStat(
                Map.of("reqJob", 1), currentWarriorWeapon, baseWarriorWeapon));
        assertTrue(AgentInventorySellTrashPolicy.hasProtectedSellTrashWeaponStat(
                Map.of("reqJob", 2), currentMageWeapon, baseMageWeapon));
    }

    private static Equip equip(int itemId) {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(itemId);
        return equip;
    }
}
