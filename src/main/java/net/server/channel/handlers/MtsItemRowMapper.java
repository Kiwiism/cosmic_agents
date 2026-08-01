package net.server.channel.handlers;

import client.inventory.Equip;
import client.inventory.Item;
import server.MTSItemInfo;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Converts one {@code mts_items} row into the domain object sent to clients. */
final class MtsItemRowMapper {
    private MtsItemRowMapper() {
    }

    static MTSItemInfo mapListing(ResultSet row) throws SQLException {
        Item item = row.getInt("type") == 1 ? mapEquip(row) : mapStackable(row);
        return new MTSItemInfo(item, row.getInt("price"), row.getInt("id"), row.getInt("seller"),
                row.getString("sellername"), row.getString("sell_ends"));
    }

    private static Item mapStackable(ResultSet row) throws SQLException {
        Item item = new Item(row.getInt("itemid"), (short) 0, (short) row.getInt("quantity"));
        item.setOwner(row.getString("owner"));
        return item;
    }

    private static Equip mapEquip(ResultSet row) throws SQLException {
        Equip equip = new Equip(row.getInt("itemid"), (short) row.getInt("position"), -1);
        equip.setOwner(row.getString("owner"));
        equip.setQuantity((short) 1);
        equip.setAcc((short) row.getInt("acc"));
        equip.setAvoid((short) row.getInt("avoid"));
        equip.setDex((short) row.getInt("dex"));
        equip.setHands((short) row.getInt("hands"));
        equip.setHp((short) row.getInt("hp"));
        equip.setInt((short) row.getInt("int"));
        equip.setJump((short) row.getInt("jump"));
        equip.setVicious((short) row.getInt("vicious"));
        equip.setLuk((short) row.getInt("luk"));
        equip.setMatk((short) row.getInt("matk"));
        equip.setMdef((short) row.getInt("mdef"));
        equip.setMp((short) row.getInt("mp"));
        equip.setSpeed((short) row.getInt("speed"));
        equip.setStr((short) row.getInt("str"));
        equip.setWatk((short) row.getInt("watk"));
        equip.setWdef((short) row.getInt("wdef"));
        equip.setUpgradeSlots((byte) row.getInt("upgradeslots"));
        equip.setLevel((byte) row.getInt("level"));
        equip.setItemLevel(row.getByte("itemlevel"));
        equip.setItemExp(row.getInt("itemexp"));
        equip.setRingId(row.getInt("ringid"));
        equip.setFlag((short) row.getInt("flag"));
        equip.setExpiration(row.getLong("expiration"));
        equip.setGiftFrom(row.getString("giftFrom"));
        return equip;
    }
}
