package server.observer;

import client.Character;
import client.Client;

import java.util.List;

public interface ObserverCharacterDirectoryAdapter {
    List<ObserverCharacterDirectory.Entry> entries(Client observerClient);

    Character find(Client observerClient, int characterId);
}
