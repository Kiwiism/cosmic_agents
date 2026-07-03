package server.agents.capabilities.equipment;

import client.Job;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipmentStatSnapshotTest {

    @Test
    void totalAccCombinesDerivedAndFlatAccuracy() {
        AgentEquipmentStatSnapshot snapshot = new AgentEquipmentStatSnapshot(
                4, 50, 4, 30, 20, 0, 7, 35, 0, Job.ASSASSIN);

        assertEquals(62, snapshot.totalAcc());
    }

    @Test
    void swapAppliesAddedMinusRemovedEquipmentStats() {
        Equip removed = equip(1, 2, 3, 4, 5, 6, 7);
        Equip added = equip(4, 6, 8, 10, 12, 14, 16);
        AgentEquipmentStatSnapshot snapshot = new AgentEquipmentStatSnapshot(
                10, 20, 30, 40, 50, 60, 70, 80, 90, Job.BOWMAN);

        AgentEquipmentStatSnapshot swapped = snapshot.swap(removed, added);

        assertEquals(13, swapped.str());
        assertEquals(24, swapped.dex());
        assertEquals(35, swapped.int_());
        assertEquals(46, swapped.luk());
        assertEquals(57, swapped.watk());
        assertEquals(73, swapped.magic());
        assertEquals(79, swapped.flatAcc());
        assertEquals(80, swapped.level());
        assertEquals(90, swapped.fame());
        assertEquals(Job.BOWMAN, swapped.job());
    }

    private static Equip equip(int str, int dex, int int_, int luk, int watk, int matk, int acc) {
        Equip equip = mock(Equip.class);
        when(equip.getStr()).thenReturn((short) str);
        when(equip.getDex()).thenReturn((short) dex);
        when(equip.getInt()).thenReturn((short) int_);
        when(equip.getLuk()).thenReturn((short) luk);
        when(equip.getWatk()).thenReturn((short) watk);
        when(equip.getMatk()).thenReturn((short) matk);
        when(equip.getAcc()).thenReturn((short) acc);
        return equip;
    }
}
