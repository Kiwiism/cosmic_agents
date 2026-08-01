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
import client.processor.npc.DueyProcessor;
import config.YamlConfig;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import tools.PacketCreator;
import server.security.MutationReplayGuard;
import server.security.SecurityEventRuntime;
import server.security.SecurityEventType;
import server.security.SecuritySeverity;

import java.util.Map;

public final class DueyHandler extends AbstractPacketHandler {

    @Override
    public final void handlePacket(InPacket p, Client c) {
        if (!YamlConfig.config.server.USE_DUEY) {
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        byte operation = p.readByte();
        if (!DueyRequestValidator.hasValidEnvelope(operation, p.available())) {
            reject(c, operation, "invalid-envelope");
            return;
        }
        if (operation == DueyProcessor.Actions.TOSERVER_RECV_ITEM.getCode()) { // on click 'O' Button, thanks inhyuk
            DueyProcessor.dueySendTalk(c, false);
        } else if (operation == DueyProcessor.Actions.TOSERVER_SEND_ITEM.getCode()) {
            byte inventId = p.readByte();
            short itemPos = p.readShort();
            short amount = p.readShort();
            int mesos = p.readInt();
            String recipient = p.readString();
            boolean quick = p.readByte() != 0;
            String message = quick ? p.readString() : null;

            DueyProcessor.dueySendItem(c, inventId, itemPos, amount, mesos, message, recipient, quick);
        } else if (operation == DueyProcessor.Actions.TOSERVER_REMOVE_PACKAGE.getCode()) {
            int packageid = p.readInt();
            if (!MutationReplayGuard.acquire(c.getPlayer().getId(), "DUEY_REMOVE", packageid)) {
                reject(c, operation, "duplicate-resource-mutation");
                return;
            }
            DueyProcessor.dueyRemovePackage(c, packageid, true);
        } else if (operation == DueyProcessor.Actions.TOSERVER_CLAIM_PACKAGE.getCode()) {
            int packageid = p.readInt();
            if (!MutationReplayGuard.acquire(c.getPlayer().getId(), "DUEY_CLAIM", packageid)) {
                reject(c, operation, "duplicate-resource-mutation");
                return;
            }
            DueyProcessor.dueyClaimPackage(c, packageid);
        }
    }

    private static void reject(Client client, byte operation, String reason) {
        SecurityEventType type = "duplicate-resource-mutation".equals(reason)
                ? SecurityEventType.MUTATION_REPLAY : SecurityEventType.MALFORMED_PACKET;
        SecurityEventRuntime.record(client, type, SecuritySeverity.WARNING,
                Map.of("packetFamily", "DUEY", "operation", Byte.toString(operation), "reason", reason));
        client.sendPacket(PacketCreator.sendDueyMSG(
                DueyProcessor.Actions.TOCLIENT_RECV_UNKNOWN_ERROR.getCode()));
    }
}
