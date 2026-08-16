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
 */
package net.server.channel.handlers;

import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.ScrollTransactionService;

/** @author Matze @author Frz */
public final class ScrollHandler extends AbstractPacketHandler {
    @Override
    public void handlePacket(InPacket packet, Client client) {
        if (!client.tryacquireClient()) return;
        try {
            packet.readInt();
            short scrollSlot = packet.readShort();
            short equipSlot = packet.readShort();
            byte flags = (byte) packet.readShort();
            ScrollTransactionService.apply(client, scrollSlot, equipSlot, flags);
        } finally {
            client.releaseClient();
        }
    }
}
