package server;

import client.inventory.Equip;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.wz.WZFiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInformationProviderCashItemTest {
    @Test
    void vegaCompatibilityComesFromOfficialWzListAndRate() {
        Data vegaData = DataProviderFactory.getDataProvider(WZFiles.ETC).getData("VegaSpell.img");
        Map<Integer, Integer> compatibility = CashItemEligibility.loadVegaSpellScrolls(vegaData);

        assertEquals(201, compatibility.size());
        assertEquals(ItemId.VEGAS_SPELL_10, compatibility.get(2040002));
        assertEquals(ItemId.VEGAS_SPELL_60, compatibility.get(2040001));
        assertFalse(compatibility.containsKey(2040000));
    }

    @Test
    void hammerRequiresUpgradeableNonCashEquipmentAndHonorsUsageCap() {
        Equip upgradeable = Equip.restored(1002001, (short) 1);
        assertTrue(CashItemEligibility.canUseViciousHammer(upgradeable, Map.of("tuc", 7, "cash", 0)));

        upgradeable.setVicious(2);
        assertFalse(CashItemEligibility.canUseViciousHammer(upgradeable, Map.of("tuc", 7, "cash", 0)));
        assertFalse(CashItemEligibility.canUseViciousHammer(
                Equip.restored(1122000, (short) 1), Map.of("tuc", 3, "cash", 0)));
        assertFalse(CashItemEligibility.canUseViciousHammer(
                Equip.restored(1002001, (short) 1), Map.of("tuc", 0, "cash", 0)));
        assertFalse(CashItemEligibility.canUseViciousHammer(
                Equip.restored(1002001, (short) 1), Map.of("tuc", 7, "cash", 1)));
        assertFalse(CashItemEligibility.canUseViciousHammer(null, null));
    }
}
