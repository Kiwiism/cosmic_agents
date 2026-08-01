package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.observer.ObserverAuthorizationService;
import server.observer.ObserverFeature;
import server.monitoring.ThrottledLogger;
import tools.PacketCreator;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ObserverCharactersHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(ObserverCharactersHandler.class);
    private static final Map<Client, Integer> LAST_DIRECTORY_SIGNATURE = new WeakHashMap<>();

    @Override
    public void handlePacket(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (!ObserverFeature.enabled()) {
            logRejected(client, "observer feature is disabled");
            return;
        }
        if (observer == null) {
            logRejected(client, "channel client has no player");
            return;
        }
        if (!ObserverAuthorizationService.mayUse(client)) {
            logRejected(client, "not authorized (gmLevel=" + client.getGMLevel() + ")");
            return;
        }

        List<server.observer.ObserverCharacterDirectory.Entry> characters =
                server.observer.ObserverCharacterDirectory.entries(client);
        logDirectoryChange(client, observer, characters);
        client.sendPacket(PacketCreator.observerCharacters(characters));
    }

    private static void logDirectoryChange(
            Client client,
            Character observer,
            List<server.observer.ObserverCharacterDirectory.Entry> characters) {
        int signature = characters.hashCode();
        synchronized (LAST_DIRECTORY_SIGNATURE) {
            Integer previous = LAST_DIRECTORY_SIGNATURE.put(client, signature);
            if (previous != null && previous == signature) {
                return;
            }
        }
        log.info("[observer] character directory observer={} count={} entries={}",
                observer.getName(), characters.size(),
                characters.stream().map(server.observer.ObserverCharacterDirectory.Entry::name).toList());
    }

    private static void logRejected(Client client, String reason) {
        String account = client == null ? "?" : client.getAccountName();
        ThrottledLogger.warn(
                "observer-character-directory:" + account + ":" + reason,
                log,
                "[observer] character directory request rejected account={}: {}",
                null,
                account,
                reason);
    }
}
