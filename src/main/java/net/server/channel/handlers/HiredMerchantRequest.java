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
import client.inventory.ItemFactory;
import constants.game.GameConstants;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.maps.reservation.FreeMarketStorePlacementService;
import tools.PacketCreator;

import java.sql.SQLException;

/**
 * @author XoticStory
 */
public final class HiredMerchantRequest extends AbstractPacketHandler {
    @Override
    public final void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();

        if (GameConstants.isFreeMarketRoom(chr.getMapId())) {
            if (!chr.hasMerchant()) {
                if (!FreeMarketStorePlacementService.hasAvailablePlacement(chr)) {
                    chr.sendPacket(PacketCreator.getMiniRoomError(13));
                    chr.dropMessage(5, "No designated Free Market store spot is available nearby.");
                    return;
                }
                try {
                    if (ItemFactory.MERCHANT.loadItems(chr.getId(), false).isEmpty() && chr.getMerchantMeso() == 0) {
                        c.sendPacket(PacketCreator.hiredMerchantBox());
                    } else {
                        chr.sendPacket(PacketCreator.retrieveFirstMessage());
                    }
                } catch (SQLException ex) {
                    monitoring.RuntimeFailureLogger.log(ex);
                }
            } else {
                chr.dropMessage(1, "You already have a store open.");
            }
        } else {
            chr.dropMessage(1, "You cannot open your hired merchant here.");
        }
    }
}
