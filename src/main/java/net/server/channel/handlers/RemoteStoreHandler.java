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

import client.Character;
import client.Client;
import client.inventory.InventoryType;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.maps.HiredMerchant;
import tools.PacketCreator;

/**
 * @author kevintjuh93 - :3
 */
public class RemoteStoreHandler extends AbstractPacketHandler {
    private static final int STORE_REMOTE_CONTROLLER = 5470000;

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();
        HiredMerchant hm = getMerchant(c);
        if (hm != null && hm.isOwner(chr)) {
            if (isRemoteAccess(chr, hm) && !hasRemoteStoreController(chr)) {
                chr.dropMessage(1, "You need a Store Remote Controller to access your merchant from here.");
                c.sendPacket(PacketCreator.enableActions());
                return;
            }
            if (hm.getChannel() == chr.getClient().getChannel()) {
                hm.visitShop(chr);
            } else {
                c.sendPacket(PacketCreator.remoteChannelChange((byte) (hm.getChannel() - 1)));
            }
            return;
        } else {
            chr.dropMessage(1, "You don't have a Merchant open.");
        }
        c.sendPacket(PacketCreator.enableActions());
    }

    private static HiredMerchant getMerchant(Client c) {
        if (c.getPlayer().hasMerchant()) {
            return c.getWorldServer().getHiredMerchant(c.getPlayer().getId());
        }
        return null;
    }

    private static boolean isRemoteAccess(Character chr, HiredMerchant hm) {
        return hm.getChannel() != chr.getClient().getChannel()
                || chr.getMapId() != hm.getMapId();
    }

    private static boolean hasRemoteStoreController(Character chr) {
        return chr.getInventory(InventoryType.CASH).countById(STORE_REMOTE_CONTROLLER) > 0;
    }
}
