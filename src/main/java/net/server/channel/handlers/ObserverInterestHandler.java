package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.agents.auth.AgentAuthorityService;
import server.agents.observer.SpectatorInterestService;
import server.agents.observer.SpectatorAgentSignalService;
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
        if (observer == null
                || (client.getGMLevel() < 2 && !AgentAuthorityService.mayObserve(observer))
                || packet.available() < 9
                || (packet.readByte() & 0xFF) != VERSION) {
            return;
        }
        long afterSequence = Math.max(0L, packet.readLong());
        if (rateLimited(client)) {
            return;
        }
        SpectatorAgentSignalService.sampleWorld(observer.getWorld());
        client.sendPacket(PacketCreator.observerInterestEvents(
                SpectatorInterestService.latestSequence(observer.getWorld()),
                SpectatorInterestService.eventsSince(
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
