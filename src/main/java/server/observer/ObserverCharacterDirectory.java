package server.observer;

import client.Character;
import client.Client;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ObserverCharacterDirectory {
    public record Entry(int id, int mapId, int channel, String name) {
    }

    private ObserverCharacterDirectory() {
    }

    public static List<Entry> entries(Client client) {
        Character observer = client == null ? null : client.getPlayer();
        if (client == null || observer == null) {
            return List.of();
        }

        Map<Integer, Entry> entries = new LinkedHashMap<>();
        client.getWorldServer().getPlayerStorage().getAllCharacters().stream()
                .filter(character -> character != observer)
                .filter(Character::isLoggedin)
                .filter(character -> character.getClient() != null)
                .forEach(character -> entries.put(character.getId(), new Entry(
                        character.getId(),
                        character.getMapId(),
                        character.getClient().getChannel(),
                        character.getName())));
        ObserverAdapters.characterDirectory().ifPresent(adapter ->
                adapter.entries(client).forEach(entry -> entries.putIfAbsent(entry.id(), entry)));
        return entries.values().stream()
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static Character find(Client client, int characterId) {
        if (client == null || characterId <= 0) {
            return null;
        }
        Character character = client.getWorldServer().getPlayerStorage()
                .getCharacterById(characterId);
        if (character != null) {
            return character;
        }
        return ObserverAdapters.characterDirectory()
                .map(adapter -> adapter.find(client, characterId))
                .orElse(null);
    }
}
