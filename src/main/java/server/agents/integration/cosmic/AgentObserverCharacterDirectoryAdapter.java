package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.observer.ObserverCharacterDirectory;
import server.observer.ObserverCharacterDirectoryAdapter;

import java.util.List;

public final class AgentObserverCharacterDirectoryAdapter
        implements ObserverCharacterDirectoryAdapter {
    @Override
    public List<ObserverCharacterDirectory.Entry> entries(Client observerClient) {
        if (observerClient == null) {
            return List.of();
        }
        return AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(character -> character != null && character.getMap() != null)
                .filter(character -> character.getMap().getWorld() == observerClient.getWorld())
                .map(character -> new ObserverCharacterDirectory.Entry(
                        character.getId(),
                        character.getMapId(),
                        character.getMap().getChannelServer().getId(),
                        character.getName()))
                .toList();
    }

    @Override
    public Character find(Client observerClient, int characterId) {
        var entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character character = AgentRuntimeIdentityRuntime.bot(entry);
        if (character == null || character.getMap() == null || observerClient == null
                || character.getMap().getWorld() != observerClient.getWorld()) {
            return null;
        }
        return character;
    }
}
