package net.server.channel.handlers;

import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.observer.ObserverAdapters;
import server.observer.ObserverAuthorizationService;
import server.observer.ObserverFeature;
import server.monitoring.ThrottledLogger;

public final class ObserverNavGraphHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(ObserverNavGraphHandler.class);

    @Override
    public void handlePacket(InPacket packet, Client client) {
        if (!ObserverFeature.navGraphEnabled()) {
            logRejected("disabled", "observer navgraph is disabled");
            return;
        }
        if (client.getPlayer() == null) {
            logRejected("no-player", "channel client has no player");
            return;
        }
        if (!ObserverAuthorizationService.mayUse(client)) {
            logRejected(
                    "unauthorized:" + client.getAccountName(),
                    "not authorized for " + client.getPlayer().getName()
                            + " (gmLevel=" + client.getGMLevel() + ")");
            return;
        }
        ObserverAdapters.navGraph().ifPresentOrElse(
                adapter -> {
                    log.info("[observer] navgraph request received observer={} mapId={} bytes={}",
                            client.getPlayer().getName(), client.getPlayer().getMapId(), packet.available());
                    adapter.handle(packet, client);
                },
                () -> logRejected(
                        "missing-adapter",
                        "no adapter was discovered for " + client.getPlayer().getName()));
    }

    private static void logRejected(String key, String reason) {
        ThrottledLogger.warn(
                "observer-navgraph:" + key,
                log,
                "[observer] navgraph request rejected: {}",
                null,
                reason);
    }
}
