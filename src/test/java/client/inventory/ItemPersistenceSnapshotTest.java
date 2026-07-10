package client.inventory;

import org.junit.jupiter.api.Test;
import tools.Pair;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPersistenceSnapshotTest {
    @Test
    void itemCopyPreservesEveryPersistedInventoryColumn() {
        Item original = new Item(2000000, (short) 7, (short) 23);
        original.setOwner("owner");
        original.setExpiration(-1);
        original.setGiftFrom("sender");

        Item copy = original.copy();

        assertEquals(original.getItemId(), copy.getItemId());
        assertEquals(original.getPosition(), copy.getPosition());
        assertEquals(original.getQuantity(), copy.getQuantity());
        assertEquals(original.getOwner(), copy.getOwner());
        assertEquals(original.getPetId(), copy.getPetId());
        assertEquals(original.getFlag(), copy.getFlag());
        assertEquals(original.getExpiration(), copy.getExpiration());
        assertEquals(original.getGiftFrom(), copy.getGiftFrom());
    }

    @Test
    void equipCopyPreservesEveryPersistedEquipmentColumn() {
        Equip original = new Equip(1002000, (short) -1, 7, false);
        original.setOwner("owner");
        original.setFlag((short) 2);
        original.setGiftFrom("sender");
        original.setLevel((byte) 3);
        original.setStr((short) 1);
        original.setDex((short) 2);
        original.setInt((short) 3);
        original.setLuk((short) 4);
        original.setHp((short) 5);
        original.setMp((short) 6);
        original.setWatk((short) 7);
        original.setMatk((short) 8);
        original.setWdef((short) 9);
        original.setMdef((short) 10);
        original.setAcc((short) 11);
        original.setAvoid((short) 12);
        original.setHands((short) 13);
        original.setSpeed((short) 14);
        original.setJump((short) 15);
        original.setVicious((short) 1);
        original.setItemLevel((byte) 4);
        original.setItemExp(123);
        original.setRingId(99);

        Equip copy = (Equip) original.copy();

        assertEquals(original.getItemId(), copy.getItemId());
        assertEquals(original.getPosition(), copy.getPosition());
        assertEquals(original.getQuantity(), copy.getQuantity());
        assertEquals(original.getOwner(), copy.getOwner());
        assertEquals(original.getFlag(), copy.getFlag());
        assertEquals(original.getExpiration(), copy.getExpiration());
        assertEquals(original.getGiftFrom(), copy.getGiftFrom());
        assertEquals(original.getUpgradeSlots(), copy.getUpgradeSlots());
        assertEquals(original.getLevel(), copy.getLevel());
        assertEquals(original.getStr(), copy.getStr());
        assertEquals(original.getDex(), copy.getDex());
        assertEquals(original.getInt(), copy.getInt());
        assertEquals(original.getLuk(), copy.getLuk());
        assertEquals(original.getHp(), copy.getHp());
        assertEquals(original.getMp(), copy.getMp());
        assertEquals(original.getWatk(), copy.getWatk());
        assertEquals(original.getMatk(), copy.getMatk());
        assertEquals(original.getWdef(), copy.getWdef());
        assertEquals(original.getMdef(), copy.getMdef());
        assertEquals(original.getAcc(), copy.getAcc());
        assertEquals(original.getAvoid(), copy.getAvoid());
        assertEquals(original.getHands(), copy.getHands());
        assertEquals(original.getSpeed(), copy.getSpeed());
        assertEquals(original.getJump(), copy.getJump());
        assertEquals(original.getVicious(), copy.getVicious());
        assertEquals(original.getItemLevel(), copy.getItemLevel());
        assertEquals(original.getItemExp(), copy.getItemExp());
        assertEquals(original.getRingId(), copy.getRingId());
    }

    @Test
    void bulkEquipStatMutationSignalsAfterApplyingTheNewValue() {
        Equip equip = new Equip(1002000, (short) -1, 7, false);
        AtomicBoolean observedAppliedValue = new AtomicBoolean();
        equip.setPersistenceDirtyMarker(() -> observedAppliedValue.set(equip.getStr() == 3));

        equip.gainStats(List.of(new Pair<>(Equip.StatUpgrade.incSTR, 3)));

        assertEquals(3, equip.getStr());
        assertTrue(observedAppliedValue.get());
    }
}
