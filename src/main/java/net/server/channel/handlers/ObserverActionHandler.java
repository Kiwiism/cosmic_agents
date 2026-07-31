package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.agents.auth.AgentAuthorityService;
import server.agents.observer.ObserverFeature;
import server.agents.observer.ObserverWarpService;
import server.agents.observer.protocol.ObserverActionProtocol;
import tools.PacketCreator;

import java.util.Map;
import java.util.WeakHashMap;

public final class ObserverActionHandler extends AbstractPacketHandler {
    private static final int REQUEST_BYTES = 10;
    private static final long MIN_REQUEST_INTERVAL_NANOS = 250_000_000L;
    private static final Map<Client, Long> LAST_REQUEST_NANOS = new WeakHashMap<>();

    @Override
    public void handlePacket(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (!ObserverFeature.enabled()
                || observer == null
                || (client.getGMLevel() < 2 && !AgentAuthorityService.mayObserve(observer))
                || packet.available() < REQUEST_BYTES) {
            return;
        }

        int version = packet.readByte() & 0xFF;
        int action = packet.readByte() & 0xFF;
        int requestId = packet.readInt();
        int targetId = packet.readInt();
        if (version != ObserverActionProtocol.VERSION
                || !ObserverActionProtocol.validAction(action)
                || requestId <= 0
                || rateLimited(client)) {
            return;
        }

        ObserverWarpService.Result result =
                action == ObserverActionProtocol.ACTION_WARP_MAP
                        ? ObserverWarpService.warpMap(client, targetId)
                        : ObserverWarpService.warpCharacter(client, targetId);
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
