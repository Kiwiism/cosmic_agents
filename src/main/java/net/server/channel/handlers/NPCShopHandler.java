/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.server.channel.handlers;

import client.Client;
import client.autoban.AutobanFactory;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Shop;
import server.security.SecurityEventRuntime;
import server.security.SecurityEventType;
import server.security.SecuritySeverity;
import tools.PacketCreator;

import java.util.Map;

/**
 * @author Matze
 */
public final class NPCShopHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(NPCShopHandler.class);

    @Override
    public void handlePacket(InPacket p, Client c) {
        byte bmode = p.readByte();
        Shop shop = c.getPlayer().getShop();
        if (bmode != 3 && shop == null) {
            reject(c, bmode, "no-active-shop");
            return;
        }
        switch (bmode) {
        case 0: { // mode 0 = buy :)
            short slot = p.readShort();// slot
            int itemId = p.readInt();
            short quantity = p.readShort();
            if (quantity < 1) {
                AutobanFactory.PACKET_EDIT.alert(c.getPlayer(),
                        c.getPlayer().getName() + " tried to packet edit a npc shop.");
                log.warn("Chr {} tried to buy quantity {} of itemid {}", c.getPlayer().getName(), quantity, itemId);
                c.disconnect(true, false);
                return;
            }
            shop.buy(c, slot, itemId, quantity);
            break;
        }
        case 1: { // sell ;)
            short slot = p.readShort();
            int itemId = p.readInt();
            short quantity = p.readShort();
            InventoryType type = ItemConstants.getInventoryType(itemId);
            Inventory inventory = type == InventoryType.UNDEFINED ? null : c.getPlayer().getInventory(type);
            Item item = inventory == null ? null : inventory.getItem(slot);
            if (quantity < 1 || item == null || item.getItemId() != itemId) {
                reject(c, bmode, "sale-item-mismatch");
                return;
            }
            shop.sell(c, type, slot, quantity);
            break;
        }
        case 2: { // recharge ;)
            short slot = p.readShort();
            if (slot < 1 || slot > Byte.MAX_VALUE) {
                reject(c, bmode, "invalid-recharge-slot");
                return;
            }
            shop.recharge(c, slot);
            break;
        }
        case 3: // leaving :(
            c.getPlayer().setShop(null);
            break;
        default:
            reject(c, bmode, "unknown-shop-action");
            break;
        }

    }

    private static void reject(Client client, byte mode, String reason) {
        SecurityEventRuntime.record(client, SecurityEventType.MALFORMED_PACKET, SecuritySeverity.WARNING,
                Map.of("packetFamily", "NPC_SHOP", "mode", Byte.toString(mode), "reason", reason));
        client.sendPacket(PacketCreator.enableActions());
    }
}
