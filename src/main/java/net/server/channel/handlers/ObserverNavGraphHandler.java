package net.server.channel.handlers;

import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.observer.ObserverAdapters;
import server.observer.ObserverAuthorizationService;
import server.observer.ObserverFeature;

public final class ObserverNavGraphHandler extends AbstractPacketHandler {
    @Override
    public void handlePacket(InPacket packet, Client client) {
        if (!ObserverFeature.navGraphEnabled()
                || client.getPlayer() == null
                || !ObserverAuthorizationService.mayUse(client)) {
            return;
        }
        ObserverAdapters.navGraph().ifPresent(adapter -> adapter.handle(packet, client));
    }
}
