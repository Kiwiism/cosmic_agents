package net.server.channel.handlers;

import client.inventory.Equip;
import client.inventory.Item;
import org.mockito.MockedConstruction;
import org.junit.jupiter.api.Test;
import server.MTSItemInfo;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MtsItemRowMapperTest {
    @Test
    void mapsStackableListingMetadata() throws Exception {
        ResultSet row = baseRow(2, 2000000);
        when(row.getInt("quantity")).thenReturn(37);
        MTSItemInfo listing = MtsItemRowMapper.mapListing(row);
        Item item = listing.getItem();

        assertEquals(2000000, item.getItemId());
        assertEquals(37, item.getQuantity());
        assertEquals("Owner", item.getOwner());
        assertListingMetadata(listing);
    }

    @Test
    void mapsEquipmentListingFromTheSuppliedRow() throws Exception {
        ResultSet row = baseRow(1, 1002000);
        when(row.getInt("position")).thenReturn(3);
        when(row.getInt("str")).thenReturn(11);
        when(row.getInt("dex")).thenReturn(12);
        when(row.getInt("vicious")).thenReturn(2);
        when(row.getInt("upgradeslots")).thenReturn(5);
        when(row.getInt("level")).thenReturn(4);
        when(row.getByte("itemlevel")).thenReturn((byte) 6);
        when(row.getInt("itemexp")).thenReturn(700);
        when(row.getInt("ringid")).thenReturn(88);
        when(row.getInt("flag")).thenReturn(1);
        when(row.getLong("expiration")).thenReturn(987654321L);
        when(row.getString("giftFrom")).thenReturn("Bob");

        try (MockedConstruction<Equip> construction = mockConstruction(Equip.class)) {
            MTSItemInfo listing = MtsItemRowMapper.mapListing(row);
            Equip equip = construction.constructed().getFirst();

            assertSame(equip, assertInstanceOf(Equip.class, listing.getItem()));
            verify(equip).setStr((short) 11);
            verify(equip).setDex((short) 12);
            verify(equip).setVicious((short) 2);
            verify(equip).setUpgradeSlots((byte) 5);
            verify(equip).setLevel((byte) 4);
            verify(equip).setItemLevel((byte) 6);
            verify(equip).setItemExp(700);
            verify(equip).setRingId(88);
            verify(equip).setFlag((short) 1);
            verify(equip).setExpiration(987654321L);
            verify(equip).setGiftFrom("Bob");
            assertListingMetadata(listing);
        }
    }

    private static ResultSet baseRow(int type, int itemId) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getInt("type")).thenReturn(type);
        when(row.getInt("itemid")).thenReturn(itemId);
        when(row.getString("owner")).thenReturn("Owner");
        when(row.getInt("price")).thenReturn(1234);
        when(row.getInt("id")).thenReturn(55);
        when(row.getInt("seller")).thenReturn(77);
        when(row.getString("sellername")).thenReturn("Seller");
        when(row.getString("sell_ends")).thenReturn("2030-01-02");
        return row;
    }

    private static void assertListingMetadata(MTSItemInfo listing) {
        assertEquals(1234, listing.getPrice());
        assertEquals(55, listing.getID());
        assertEquals("Seller", listing.getSeller());
    }
}
