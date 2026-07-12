package client;

import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterProfileExchangeTest {
    @Test
    void exchangesProfileOwnedStateWithoutMovingActors() throws Exception {
        Character first = character(10, "Pio", 28, Job.ASSASSIN, 30030);
        Character second = character(20, "Yoona", 17, Job.MAGICIAN, 20020);
        first.setPosition(new Point(100, 50));
        second.setPosition(new Point(400, 80));
        first.getInventory(InventoryType.USE).addItemFromDB(new Item(2000000, (short) 1, (short) 5));
        second.getInventory(InventoryType.USE).addItemFromDB(new Item(2000001, (short) 1, (short) 7));

        Character.ProfileExchangeResult result = Character.exchangeProfileState(first, second, false);

        assertEquals(10, first.getId());
        assertEquals(20, second.getId());
        assertEquals(new Point(100, 50), first.getPosition());
        assertEquals(new Point(400, 80), second.getPosition());
        assertEquals(20, first.getProfileOwnerCharacterId());
        assertEquals(10, second.getProfileOwnerCharacterId());
        assertEquals(Job.MAGICIAN, first.getJob());
        assertEquals(Job.ASSASSIN, second.getJob());
        assertEquals(17, first.getLevel());
        assertEquals(28, second.getLevel());
        assertEquals(20020, first.getHair());
        assertEquals(30030, second.getHair());
        assertNotNull(first.getInventory(InventoryType.USE).findById(2000001));
        assertNotNull(second.getInventory(InventoryType.USE).findById(2000000));
        assertEquals(20, result.leftProfileOwnerCharacterId());
        assertEquals(10, result.rightProfileOwnerCharacterId());
        assertEquals(1L, result.leftBindingGeneration());
        assertEquals(1L, result.rightBindingGeneration());
    }

    @Test
    void secondExchangeRestoresCanonicalOrientation() throws Exception {
        Character first = character(10, "Pio", 28, Job.ASSASSIN, 30030);
        Character second = character(20, "Yoona", 17, Job.MAGICIAN, 20020);

        Character.exchangeProfileState(first, second, false);
        Character.exchangeProfileState(first, second, false);

        assertEquals(10, first.getProfileOwnerCharacterId());
        assertEquals(20, second.getProfileOwnerCharacterId());
        assertEquals(Job.ASSASSIN, first.getJob());
        assertEquals(Job.MAGICIAN, second.getJob());
        assertEquals(2L, first.getProfileBindingGeneration());
        assertEquals(2L, second.getProfileBindingGeneration());
    }

    private static Character character(int id,
                                       String name,
                                       int level,
                                       Job job,
                                       int hair) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(id);
        when(rs.getInt("accountid")).thenReturn(1);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getInt("level")).thenReturn(level);
        when(rs.getInt("job")).thenReturn(job.getId());
        when(rs.getInt("hair")).thenReturn(hair);
        when(rs.getInt("skincolor")).thenReturn(0);
        when(rs.getInt("str")).thenReturn(12);
        when(rs.getInt("dex")).thenReturn(5);
        when(rs.getInt("int")).thenReturn(4);
        when(rs.getInt("luk")).thenReturn(4);
        when(rs.getInt("hp")).thenReturn(50);
        when(rs.getInt("maxhp")).thenReturn(50);
        when(rs.getInt("mp")).thenReturn(5);
        when(rs.getInt("maxmp")).thenReturn(5);
        when(rs.getString("sp")).thenReturn("0,0,0,0,0,0,0,0,0,0");
        when(rs.getByte("world")).thenReturn((byte) 0);
        return Character.loadCharacterEntryFromDB(rs, null);
    }
}
