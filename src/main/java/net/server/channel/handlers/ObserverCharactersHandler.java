package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.observer.ObserverAuthorizationService;
import server.observer.ObserverFeature;
import tools.PacketCreator;

import java.util.Comparator;
import java.util.List;

public final class ObserverCharactersHandler extends AbstractPacketHandler {
    @Override
    public void handlePacket(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (!ObserverFeature.enabled()
                || observer == null
                || !ObserverAuthorizationService.mayUse(client)) {
            return;
        }

        List<Character> characters = client.getWorldServer().getPlayerStorage()
                .getAllCharacters()
                .stream()
                .filter(character -> character != observer)
                .filter(Character::isLoggedin)
                .filter(character -> character.getClient() != null)
                .sorted(Comparator.comparing(Character::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        client.sendPacket(PacketCreator.observerCharacters(characters));
    }
}
