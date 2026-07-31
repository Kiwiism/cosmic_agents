package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.observer.ObserverAdapters;
import server.observer.ObserverAuthorizationService;
import server.observer.ObserverFeature;
import server.observer.ObserverInterestService;
import tools.PacketCreator;

import java.util.Map;
import java.util.WeakHashMap;

public final class ObserverInterestHandler extends AbstractPacketHandler {
    private static final int VERSION = 1;
    private static final long MIN_REQUEST_INTERVAL_NANOS = 250_000_000L;
    private static final Map<Client, Long> LAST_REQUEST_NANOS = new WeakHashMap<>();

    @Override
    public void handlePacket(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (!ObserverFeature.enabled()
                || observer == null
                || !ObserverAuthorizationService.mayUse(client)
                || packet.available() < 9
                || (packet.readByte() & 0xFF) != VERSION) {
            return;
        }
        long afterSequence = Math.max(0L, packet.readLong());
        if (rateLimited(client)) {
            return;
        }
        ObserverAdapters.interest().ifPresent(
                adapter -> adapter.sampleWorld(observer.getWorld()));
        client.sendPacket(PacketCreator.observerInterestEvents(
                ObserverInterestService.latestSequence(observer.getWorld()),
                ObserverInterestService.eventsSince(
                        observer.getWorld(), afterSequence)));
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
