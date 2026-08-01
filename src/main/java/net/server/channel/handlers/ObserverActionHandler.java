package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.observer.ObserverAuthorizationService;
import server.observer.ObserverFeature;
import server.observer.ObserverWarpService;
import server.observer.protocol.ObserverActionProtocol;
import tools.PacketCreator;

import java.util.Map;
import java.util.WeakHashMap;

public final class ObserverActionHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(ObserverActionHandler.class);
    private static final int REQUEST_BYTES = 10;
    private static final long MIN_REQUEST_INTERVAL_NANOS = 250_000_000L;
    private static final Map<Client, Long> LAST_REQUEST_NANOS = new WeakHashMap<>();

    @Override
    public void handlePacket(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (!ObserverFeature.enabled()) {
            log.warn("[observer] action packet rejected: observer feature is disabled");
            return;
        }
        if (observer == null) {
            log.warn("[observer] action packet rejected: channel client has no player");
            return;
        }
        if (!ObserverAuthorizationService.mayUse(client)) {
            log.warn("[observer] action packet rejected for {}: not authorized (gmLevel={})",
                    observer.getName(), client.getGMLevel());
            return;
        }
        if (packet.available() < REQUEST_BYTES) {
            log.warn("[observer] action packet rejected for {}: payload has {} byte(s), expected {}",
                    observer.getName(), packet.available(), REQUEST_BYTES);
            return;
        }

        int version = packet.readByte() & 0xFF;
        int action = packet.readByte() & 0xFF;
        int requestId = packet.readInt();
        int targetId = packet.readInt();
        log.info("[observer] action received observer={} action={} requestId={} targetId={}",
                observer.getName(), action, requestId, targetId);
        if (version != ObserverActionProtocol.VERSION
                || !ObserverActionProtocol.validAction(action)
                || requestId <= 0) {
            log.warn("[observer] action rejected observer={} version={} action={} requestId={} targetId={}",
                    observer.getName(), version, action, requestId, targetId);
            return;
        }
        if (rateLimited(client)) {
            log.warn("[observer] action rate-limited observer={} action={} requestId={}",
                    observer.getName(), action, requestId);
            return;
        }

        ObserverWarpService.Result result =
                action == ObserverActionProtocol.ACTION_WARP_MAP
                        ? ObserverWarpService.warpMap(client, targetId)
                        : ObserverWarpService.warpCharacter(client, targetId);
        log.info("[observer] action result observer={} action={} requestId={} status={} mapId={} characterId={} message={}",
                observer.getName(), action, requestId, result.status(), result.mapId(),
                result.characterId(), result.message());
        client.sendPacket(PacketCreator.observerActionResult(
                action,
                requestId,
                result));
    }

    private static boolean rateLimited(Client client) {
        long now = System.nanoTime();
        synchronized (LAST_REQUEST_NANOS) {
            long previous = LAST_REQUEST_NANOS.getOrDefault(client, 0L);
            if (now - previous < MIN_REQUEST_INTERVAL_NANOS) {
                return true;
            }
            LAST_REQUEST_NANOS.put(client, now);
            return false;
        }
    }
}
